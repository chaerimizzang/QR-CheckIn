package com.qrcheckin.attendance;

/**
 * 결석 기준(F). 학교 규칙: 수업일수의 2/3 이상 출석하지 못하면 F(= 결석이 1/3 을 넘으면 F).
 * 비율은 설정값 app.attendance.fail-ratio(결석 허용 비율, 기본 1/3).
 * - 전체 수업 = 이 교육의 모든 회차(예정 포함)
 * - 결석은 인정 결석(결석 + 지각 3회당 1회)으로 센다. 출석 인정이 승인되면 출석으로 바뀐다
 *
 * 남은 결석(remaining) = 허용 결석 − 인정 결석
 *   결석 0 또는 remaining ≥ 2 → SAFE(안전) · 1 → WARNING(주의: 2번 더 결석하면 F)
 *   0 → DANGER(위험: 1번만 더 결석해도 F) · 음수 → FAIL(F 기준 넘음)
 */
public final class AbsenceRule {

    public enum Level { NONE, SAFE, WARNING, DANGER, FAIL }

    /**
     * @param plannedSessions 전체 회차 수
     * @param allowedAbsences 허용되는 인정 결석 수(이보다 많으면 F)
     * @param remaining       F 가 되기 전까지 더 결석할 수 있는 수(회차가 없으면 null)
     */
    public record Status(int plannedSessions, int allowedAbsences, Integer remaining, Level level) {
    }

    /** 지각 몇 번이 결석 1번이 되는지 */
    public static final int LATES_PER_ABSENCE = 3;

    private AbsenceRule() {
    }

    /** 인정 결석 = 결석 + 지각 3회당 1회(출석률·경고·AI 도우미 가정 계산이 모두 이 식을 쓴다) */
    public static long convertedAbsences(long absent, long late) {
        return absent + late / LATES_PER_ABSENCE;
    }

    public static Status of(int plannedSessions, long convertedAbsences, double failRatio) {
        if (plannedSessions <= 0) {
            return new Status(0, 0, null, Level.NONE);
        }
        // 1/3 같은 비율은 소수로 딱 떨어지지 않아(0.3333…) 15 × 1/3 이 4.999… 가 될 수 있다 → 작은 여유를 둔다
        int allowed = (int) Math.floor(plannedSessions * failRatio + 1e-6);
        int remaining = (int) (allowed - convertedAbsences);
        // 결석이 하나도 없으면 회차가 적은 교육(허용 1회)이어도 경고하지 않는다
        Level level = convertedAbsences == 0 ? Level.SAFE
                : remaining < 0 ? Level.FAIL
                : remaining == 0 ? Level.DANGER
                : remaining == 1 ? Level.WARNING
                : Level.SAFE;
        return new Status(plannedSessions, allowed, remaining, level);
    }

    /** 비율을 사람이 읽는 말로: 1/3·1/4·2/3 처럼 분모 2~10 분수면 분수, 아니면 퍼센트(예: 30%) */
    public static String ratioLabel(double ratio) {
        for (int d = 2; d <= 10; d++) {
            long n = Math.round(ratio * d);
            if (n > 0 && n < d && Math.abs(ratio - (double) n / d) < 1e-4) {
                return n + "/" + d;
            }
        }
        return Math.round(ratio * 100) + "%";
    }

    /** 출결 규칙 한 줄 설명(화면·AI 도우미 근거 카드). 예: 수업의 2/3 이상 출석해야 해요(결석이 1/3을 넘으면 F 기준) */
    public static String ruleText(double failRatio) {
        String fail = ratioLabel(failRatio);
        String attend = ratioLabel(1 - failRatio);
        return "수업의 " + attend + " 이상 출석해야 해요(결석이 " + fail + (fail.endsWith("%") ? "를" : "을")
                + " 넘으면 F 기준)";
    }

    /** 알림·화면에 쓰는 한 줄 */
    public static String message(Status s) {
        return switch (s.level()) {
            case FAIL -> "결석이 F 기준을 넘었어요";
            case DANGER -> "1번만 더 결석해도 F 기준을 넘어요";
            case WARNING -> "2번 더 결석하면 F 기준을 넘어요";
            default -> "";
        };
    }
}
