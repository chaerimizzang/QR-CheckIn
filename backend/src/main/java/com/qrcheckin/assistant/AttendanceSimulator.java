package com.qrcheckin.assistant;

import com.qrcheckin.attendance.AbsenceRule;

/**
 * AI 도우미의 가정 계산: "지각·결석이 n번 더 생기면?"
 * 출석률·결석 경고와 똑같이 AbsenceRule 을 쓰고, DB 에는 아무것도 저장하지 않는다.
 */
public final class AttendanceSimulator {

    /** 한 번에 가정할 수 있는 최대 횟수(지각 + 결석) */
    public static final int MAX_EXTRA = 20;

    /** 한 시점의 출결 상태(현재 또는 가정 후) */
    public record Snapshot(
            long absentCount,
            long lateCount,
            /** 인정 결석 = 결석 + ⌊지각/3⌋ */
            long convertedAbsences,
            int allowedAbsences,
            /** F 기준까지 더 결석할 수 있는 수(회차가 없으면 null, 음수면 이미 넘음) */
            Integer remainingAbsences,
            AbsenceRule.Level level,
            /** 경고 문구(안전이면 빈 문자열) — 화면·알림과 같은 문구 */
            String message) {
    }

    /**
     * @param plannedSessions   전체 회차(예정 포함) — F 기준의 분모
     * @param remainingSessions 앞으로 남은 회차(가정할 수 있는 최대 횟수)
     */
    public record Result(
            int plannedSessions,
            int remainingSessions,
            int addLate,
            int addAbsent,
            Snapshot current,
            Snapshot simulated,
            boolean levelChanged) {
    }

    private AttendanceSimulator() {
    }

    public static Snapshot snapshot(int plannedSessions, long absent, long late, double failRatio) {
        long converted = AbsenceRule.convertedAbsences(absent, late);
        AbsenceRule.Status status = AbsenceRule.of(plannedSessions, converted, failRatio);
        return new Snapshot(absent, late, converted, status.allowedAbsences(), status.remaining(), status.level(),
                AbsenceRule.message(status));
    }

    /**
     * 현재 결석·지각에 addLate·addAbsent 를 더한 결과.
     * 남은 회차보다 많이 가정하거나 음수·0회를 가정하면 IllegalArgumentException(메시지는 사용자에게 그대로 보여 준다).
     */
    public static Result simulate(int plannedSessions, int remainingSessions, long absent, long late,
                                  int addLate, int addAbsent, double failRatio) {
        if (addLate < 0 || addAbsent < 0) {
            throw new IllegalArgumentException("지각·결석 횟수는 0 이상이어야 해요");
        }
        int extra = addLate + addAbsent;
        if (extra == 0) {
            throw new IllegalArgumentException("몇 번 더 지각·결석하는지 알려 주세요");
        }
        if (extra > MAX_EXTRA) {
            throw new IllegalArgumentException("한 번에 " + MAX_EXTRA + "회까지만 가정할 수 있어요");
        }
        if (extra > remainingSessions) {
            throw new IllegalArgumentException(remainingSessions == 0
                    ? "남은 수업이 없어서 더 가정할 수 없어요"
                    : "남은 수업이 " + remainingSessions + "회라 그보다 많이 가정할 수 없어요");
        }
        Snapshot current = snapshot(plannedSessions, absent, late, failRatio);
        Snapshot simulated = snapshot(plannedSessions, absent + addAbsent, late + addLate, failRatio);
        return new Result(plannedSessions, remainingSessions, addLate, addAbsent, current, simulated,
                current.level() != simulated.level());
    }
}
