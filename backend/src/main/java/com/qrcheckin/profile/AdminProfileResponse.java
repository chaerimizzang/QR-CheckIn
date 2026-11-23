package com.qrcheckin.profile;

import java.util.List;

/** 관리자(교수) 프로필: 내 정보 + 개설한 교육 요약 */
public record AdminProfileResponse(
        String name,
        String email,
        int courseCount,
        long studentCount,
        List<CourseSummary> courses,
        /** 학과(없으면 null) */
        String department) {

    /**
     * @param studentCount       수강생 수
     * @param sessionCount       전체 회차 수
     * @param closedSessionCount 마감된 회차 수
     */
    public record CourseSummary(
            Long courseId,
            String title,
            long studentCount,
            int sessionCount,
            int closedSessionCount) {
    }
}
