package com.qrcheckin.profile;

import java.util.List;

/** 학생 프로필: 내 정보 + 수강 중인 교육별 출석 요약 */
public record StudentProfileResponse(
        String name,
        String studentNo,
        String email,
        List<CourseSummary> courses) {

    /**
     * @param totalSessions     마감된 회차 수(공결 회차는 출석률 계산에서 빠진다)
     * @param convertedAbsences 인정 결석 = 결석 + 지각/3
     * @param attendanceRate    출석률(%) — 마감된 회차가 없으면 null
     */
    public record CourseSummary(
            Long courseId,
            String title,
            String ownerName,
            int totalSessions,
            long presentCount,
            long lateCount,
            long absentCount,
            long excusedCount,
            long convertedAbsences,
            Double attendanceRate,
            /** F 기준까지 더 결석할 수 있는 수(회차가 없으면 null) */
            Integer remainingAbsences,
            /** NONE·SAFE·WARNING·DANGER·FAIL */
            com.qrcheckin.attendance.AbsenceRule.Level warningLevel) {
    }
}
