
package com.qrcheckin.attendance;

import java.util.List;

public record AttendanceStatisticsResponse(
        Long courseId,
        List<SessionStatistics> sessions,
        List<StudentAbsenceStatistics> topAbsentStudents
) {
    public record SessionStatistics(
            Long sessionId,
            int sessionNo,
            long presentCount,
            long lateCount,
            long absentCount
    ) {}

    public record StudentAbsenceStatistics(
            Long userId,
            String studentNo,
            String name,
            long absentCount
    ) {}
}
