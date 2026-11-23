package com.qrcheckin.assistant;

/**
 * AI 도우미 답변의 이동 버튼 경로. LLM 이 만든 주소는 쓰지 않고 여기서 만든 내부 경로만 쓴다.
 * (프론트 라우트: App.jsx, 신청 폼 파라미터: StudentRequests.jsx)
 */
public final class AssistantLinks {

    private AssistantLinks() {
    }

    /** 수업 하나의 내 출결 상세 */
    public static String attendanceDetail(Long courseId) {
        return "/student/courses/" + courseId + "?from=attendance";
    }

    /** 출석 인정 신청 폼을 그 수업·회차로 열기 */
    public static String requestForm(Long courseId, Long sessionId) {
        return "/requests?new=1&course=" + courseId + "&session=" + sessionId;
    }

    /** 전체 출결 현황 */
    public static String attendanceOverview() {
        return "/attendance";
    }

    /** 시간표 */
    public static String timetable() {
        return "/timetable";
    }
}
