package com.qrcheckin.attendance;

import java.util.List;

public record AttendanceRateResponse(
        Long courseId,
        int totalSessions,
        /** 전체 회차(예정 포함)와 F 기준 허용 결석 */
        int plannedSessions,
        int allowedAbsences,
        List<StudentRate> students
) {
    public record StudentRate(
            Long userId,
            String studentNo,
            String name,
            long presentCount,
            long lateCount,
            long absentCount,
            long excusedCount,
            long convertedAbsences,
            double attendanceRate,
            /** F 기준까지 더 결석할 수 있는 수(회차가 없으면 null) */
            Integer remainingAbsences,
            /** NONE·SAFE·WARNING·DANGER·FAIL (AbsenceRule) */
            AbsenceRule.Level warningLevel
    ) {
    }
}
