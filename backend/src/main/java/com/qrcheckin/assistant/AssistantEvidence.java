package com.qrcheckin.assistant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.qrcheckin.assistant.AssistantChatDtos.Action;
import com.qrcheckin.assistant.AssistantChatDtos.Fact;
import com.qrcheckin.assistant.AssistantDtos.RequestOptionItem;
import com.qrcheckin.assistant.AssistantDtos.RequestOptionsResult;
import com.qrcheckin.assistant.AssistantDtos.SimulationResult;
import com.qrcheckin.assistant.AssistantDtos.SummaryResult;
import com.qrcheckin.assistant.AssistantDtos.UpcomingResult;
import com.qrcheckin.assistant.AssistantDtos.UpcomingSession;
import com.qrcheckin.attendance.AbsenceRule.Level;
import com.qrcheckin.profile.StudentProfileResponse.CourseSummary;

/**
 * 질문 하나에서 실행한 도구 결과를 모아 근거 카드(facts)·이동 버튼(actions)을 만들고,
 * LLM 답변의 숫자가 도구 결과에 있는 숫자인지 대조한다.
 */
class AssistantEvidence {

    static final int MAX_FACTS = 8;
    static final int MAX_ACTIONS = 4;
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final Map<String, Fact> facts = new LinkedHashMap<>();
    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final Set<Long> allowedNumbers = new HashSet<>();
    /** 기간 조회에서 찾은 회차 — 신청 버튼을 이 회차로 좁힌다 */
    private final Set<Long> sessionsInRange = new HashSet<>();
    private int successfulTools;

    /** 오늘 날짜·시각과 학생이 직접 쓴 숫자는 답변에 나와도 된다 */
    void allowFrom(String text) {
        if (text == null) {
            return;
        }
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String d = m.group();
            if (d.length() <= 12) {
                allowedNumbers.add(Long.parseLong(d));
            }
        }
    }

    void allowDate(LocalDateTime now) {
        allowFrom(now.toLocalDate().toString() + " " + now.getHour() + ":" + now.getMinute());
    }

    int successfulTools() {
        return successfulTools;
    }

    /** 도구 결과 하나를 반영. resultJson 은 LLM 에 보낸 그대로의 JSON(숫자 대조용) */
    void add(Object result, String resultJson) {
        successfulTools++;
        allowFrom(resultJson);
        if (result instanceof SummaryResult r) {
            addSummary(r);
        } else if (result instanceof SimulationResult r) {
            addSimulation(r);
        } else if (result instanceof UpcomingResult r) {
            addUpcoming(r);
        } else if (result instanceof RequestOptionsResult r) {
            addRequestOptions(r);
        }
    }

    /** 답변에 있는 숫자 중 도구 결과·질문·오늘 날짜에 없는 것(없으면 빈 목록) */
    List<String> unknownNumbers(String answer) {
        List<String> unknown = new ArrayList<>();
        if (answer == null) {
            return unknown;
        }
        Matcher m = NUMBER.matcher(answer);
        while (m.find()) {
            String d = m.group();
            if (d.length() > 12 || !allowedNumbers.contains(Long.parseLong(d))) {
                unknown.add(d);
            }
        }
        return unknown;
    }

    List<Fact> facts() {
        return new ArrayList<>(facts.values()).subList(0, Math.min(MAX_FACTS, facts.size()));
    }

    List<Action> actions() {
        List<Action> list = new ArrayList<>(actions.values());
        if (list.isEmpty()) {
            list.add(new Action("출결 현황 보기", AssistantLinks.attendanceOverview()));
        }
        return list.subList(0, Math.min(MAX_ACTIONS, list.size()));
    }

    // ---------------- 도구별 근거 ----------------

    private void addSummary(SummaryResult r) {
        List<CourseSummary> sorted = r.courses().stream()
                .sorted(Comparator.comparing((CourseSummary c) -> c.warningLevel() == null ? 0 : c.warningLevel().ordinal())
                        .reversed().thenComparing(CourseSummary::title))
                .toList();
        for (CourseSummary c : sorted) {
            fact(c.title(), "결석 " + c.absentCount() + " · 지각 " + c.lateCount() + " · 인정 결석 " + c.convertedAbsences()
                    + (c.remainingAbsences() == null ? "" : " · 남은 결석 " + c.remainingAbsences() + "회")
                    + " · " + levelLabel(c.warningLevel()));
        }
        boolean single = sorted.size() == 1;
        for (CourseSummary c : sorted) {
            if (single || isRisky(c.warningLevel())) {
                action(c.title() + " 출결 상세", AssistantLinks.attendanceDetail(c.courseId()));
            }
        }
        if (!single) {
            action("출결 현황 보기", AssistantLinks.attendanceOverview());
        }
    }

    private void addSimulation(SimulationResult r) {
        var res = r.result();
        var cur = res.current();
        var sim = res.simulated();
        fact(r.courseTitle() + " · 지금", "인정 결석 " + cur.convertedAbsences() + " / 허용 " + cur.allowedAbsences()
                + " · " + levelLabel(cur.level()));
        String what = (res.addLate() > 0 ? "지각 +" + res.addLate() : "")
                + (res.addLate() > 0 && res.addAbsent() > 0 ? ", " : "")
                + (res.addAbsent() > 0 ? "결석 +" + res.addAbsent() : "");
        fact(r.courseTitle() + " · 가정(" + what + ")", "인정 결석 " + sim.convertedAbsences() + " / 허용 "
                + sim.allowedAbsences() + " · " + levelLabel(sim.level()));
        fact("남은 수업", res.remainingSessions() + "회 (전체 " + res.plannedSessions() + "회)");
        action(r.courseTitle() + " 출결 상세", AssistantLinks.attendanceDetail(r.courseId()));
    }

    private void addUpcoming(UpcomingResult r) {
        for (UpcomingSession s : r.sessions()) {
            sessionsInRange.add(s.sessionId());
            String time = s.startAt().format(HM) + (s.endAt() == null ? "" : "~" + s.endAt().format(HM));
            fact(dayLabel(s.startAt().toLocalDate()) + " " + time, s.courseTitle());
        }
        if (r.sessions().isEmpty()) {
            fact(dayLabel(r.from()) + " ~ " + dayLabel(r.to()), "수업 없음");
        }
        action("시간표 보기", AssistantLinks.timetable());
    }

    private void addRequestOptions(RequestOptionsResult r) {
        List<RequestOptionItem> shown = r.sessions().stream()
                .filter(o -> sessionsInRange.isEmpty() || sessionsInRange.contains(o.sessionId()))
                .toList();
        for (RequestOptionItem o : shown) {
            if (!sessionsInRange.isEmpty() || o.canRequest()) {
                fact(dayLabel(o.scheduledAt().toLocalDate()) + " " + r.courseTitle(),
                        o.canRequest() ? "출석 인정 신청 가능" : o.blockedReason());
            }
        }
        shown.stream()
                .filter(RequestOptionItem::canRequest)
                .filter(o -> !sessionsInRange.isEmpty() || !o.scheduledAt().toLocalDate().isBefore(r.asOf().toLocalDate()))
                .limit(3)
                .forEach(o -> action(dayLabel(o.scheduledAt().toLocalDate()) + " " + r.courseTitle() + " 신청",
                        o.requestPath()));
    }

    // ---------------- 공통 ----------------

    private void fact(String label, String value) {
        facts.putIfAbsent(label, new Fact(label, value));
    }

    private void action(String label, String path) {
        actions.putIfAbsent(path, new Action(label, path));
    }

    static boolean isRisky(Level level) {
        return level == Level.WARNING || level == Level.DANGER || level == Level.FAIL;
    }

    static String levelLabel(Level level) {
        if (level == null) {
            return "회차 없음";
        }
        return switch (level) {
            case NONE -> "회차 없음";
            case SAFE -> "안전";
            case WARNING -> "주의";
            case DANGER -> "위험";
            case FAIL -> "F 기준 초과";
        };
    }

    static String dayLabel(LocalDate d) {
        return d.getMonthValue() + "/" + d.getDayOfMonth() + "(" + d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
    }
}
