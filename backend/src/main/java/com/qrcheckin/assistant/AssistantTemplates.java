package com.qrcheckin.assistant;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrcheckin.assistant.AssistantChatDtos.Preset;
import com.qrcheckin.assistant.AssistantDtos.RequestOptionsResult;
import com.qrcheckin.assistant.AssistantDtos.SimulationResult;
import com.qrcheckin.assistant.AssistantDtos.SummaryResult;
import com.qrcheckin.assistant.AssistantDtos.UpcomingResult;
import com.qrcheckin.assistant.AssistantDtos.UpcomingSession;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.profile.StudentProfileResponse.CourseSummary;

/**
 * 추천 질문 3개의 서버 템플릿 답변. LLM 이 꺼졌거나 실패·한도 초과일 때도 추천 질문에는 답할 수 있게 한다.
 * 숫자는 모두 도구 결과에서 그대로 가져온다.
 */
@Component
class AssistantTemplates {

    enum PresetId {
        RISK("이번 학기에 출결이 위험한 수업이 있어?"),
        ONE_MORE_ABSENCE("가장 위험한 수업에서 한 번 더 결석하면 어떻게 돼?"),
        THIS_WEEK_REQUESTS("이번 주 수업 중에 출석 인정을 신청할 수 있는 회차가 있어?");

        final String question;

        PresetId(String question) {
            this.question = question;
        }

        static PresetId find(String id) {
            if (id == null) {
                return null;
            }
            return Arrays.stream(values()).filter(p -> p.name().equals(id)).findFirst().orElse(null);
        }
    }

    private final AssistantTools tools;
    private final ObjectMapper om;

    AssistantTemplates(AssistantTools tools, ObjectMapper om) {
        this.tools = tools;
        this.om = om;
    }

    static List<Preset> presets() {
        return Arrays.stream(PresetId.values()).map(p -> new Preset(p.name(), p.question)).toList();
    }

    /** 템플릿 답변 문장(근거는 evidence 에 쌓인다) */
    String answer(PresetId preset, AuthUser user, AssistantEvidence ev, LocalDate today) {
        return switch (preset) {
            case RISK -> risk(user, ev);
            case ONE_MORE_ABSENCE -> oneMoreAbsence(user, ev);
            case THIS_WEEK_REQUESTS -> thisWeekRequests(user, ev, today);
        };
    }

    private String risk(AuthUser user, AssistantEvidence ev) {
        SummaryResult summary = add(ev, tools.attendanceSummary(user, null, false));
        List<CourseSummary> courses = summary.courses();
        if (courses.isEmpty()) {
            return "이번 학기에 수강 중인 수업이 없어요.";
        }
        List<CourseSummary> risky = courses.stream()
                .filter(c -> AssistantEvidence.isRisky(c.warningLevel()))
                .sorted(worstFirst())
                .toList();
        if (risky.isEmpty()) {
            return "이번 학기 수업 " + courses.size() + "개 모두 아직 결석 경고가 없어요.";
        }
        String list = risky.stream()
                .map(c -> c.title() + "(" + AssistantEvidence.levelLabel(c.warningLevel())
                        + (c.remainingAbsences() == null ? "" : ", 남은 결석 " + Math.max(0, c.remainingAbsences()) + "회") + ")")
                .collect(Collectors.joining(", "));
        return "출결 주의가 필요한 수업은 " + list + "예요. 수업별 자세한 기록은 아래 버튼으로 볼 수 있어요.";
    }

    private String oneMoreAbsence(AuthUser user, AssistantEvidence ev) {
        SummaryResult summary = tools.attendanceSummary(user, null, false);
        if (summary.courses().isEmpty()) {
            return "이번 학기에 수강 중인 수업이 없어요.";
        }
        for (CourseSummary c : summary.courses().stream().sorted(worstFirst()).toList()) {
            SimulationResult sim;
            try {
                sim = tools.simulate(user, c.courseId(), 0, 1);
            } catch (ApiException e) {
                continue; // 남은 수업이 없는 수업은 건너뛴다
            }
            add(ev, sim);
            var cur = sim.result().current();
            var next = sim.result().simulated();
            String level = sim.result().levelChanged()
                    ? "경고 단계가 " + AssistantEvidence.levelLabel(cur.level()) + "에서 "
                            + AssistantEvidence.levelLabel(next.level()) + "(으)로 바뀌어요"
                    : "경고 단계는 " + AssistantEvidence.levelLabel(next.level()) + " 그대로예요";
            return c.title() + "에서 한 번 더 결석하면 인정 결석이 " + cur.convertedAbsences() + "회에서 "
                    + next.convertedAbsences() + "회가 되고, " + level + ". 가정 계산이라 실제 기록은 바뀌지 않아요.";
        }
        return "남은 수업이 있는 수업이 없어서 가정 계산을 할 수 없어요.";
    }

    private String thisWeekRequests(AuthUser user, AssistantEvidence ev, LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        UpcomingResult week = add(ev, tools.sessionsBetween(user, monday, sunday));
        String range = AssistantEvidence.dayLabel(monday) + "~" + AssistantEvidence.dayLabel(sunday);
        if (week.sessions().isEmpty()) {
            return "이번 주(" + range + ")에는 수업이 없어요.";
        }
        List<UpcomingSession> held = week.sessions().stream().filter(s -> !s.canceled()).toList();
        long canceled = week.sessions().size() - held.size();
        String canceledNote = canceled == 0 ? "" : " 휴강인 수업 " + canceled + "개는 신청하지 않아도 돼요.";
        if (held.isEmpty()) {
            return "이번 주(" + range + ") 수업은 모두 휴강이에요.";
        }
        Set<Long> inRange = held.stream().map(UpcomingSession::sessionId).collect(Collectors.toSet());
        Set<Long> courseIds = held.stream().map(UpcomingSession::courseId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        long can = 0;
        for (Long courseId : courseIds) {
            RequestOptionsResult options = add(ev, tools.requestOptions(user, courseId));
            can += options.sessions().stream().filter(o -> inRange.contains(o.sessionId()) && o.canRequest()).count();
        }
        if (can == 0) {
            return "이번 주(" + range + ") 수업 " + held.size() + "개 중 지금 출석 인정을 신청할 수 있는 회차는 없어요." + canceledNote;
        }
        return "이번 주(" + range + ") 수업 " + held.size() + "개 중 출석 인정을 신청할 수 있는 회차는 "
                + can + "개예요. 아래 버튼으로 바로 신청 화면을 열 수 있어요. 인정 여부는 교수님이 결정해요." + canceledNote;
    }

    private <T> T add(AssistantEvidence ev, T result) {
        try {
            ev.add(result, om.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            ev.add(result, "");
        }
        return result;
    }

    private static Comparator<CourseSummary> worstFirst() {
        return Comparator.comparing((CourseSummary c) -> c.warningLevel() == null ? 0 : c.warningLevel().ordinal())
                .reversed()
                .thenComparing(c -> c.remainingAbsences() == null ? Integer.MAX_VALUE : c.remainingAbsences())
                .thenComparing(CourseSummary::title);
    }
}
