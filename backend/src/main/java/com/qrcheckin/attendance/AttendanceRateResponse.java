package com.qrcheckin.attendance;

import java.util.List;

public record AttendanceRateResponse(
        Long courseId,
        int totalSessions,
        List<StudentRate> students
) {
    public record StudentRate(
            Long userId,
            String studentNo,
            String name,
            long presentCount,
            long lateCount,
            long absentCount,
            long convertedAbsences,
            double attendanceRate
    ) {
    }
}
