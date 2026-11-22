package com.qrcheckin.assistant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.qrcheckin.attendance.AttendanceStatus;
import com.qrcheckin.profile.StudentProfileResponse.CourseSummary;
import com.qrcheckin.schedule.ScheduleDtos.ScheduleSlot;
import com.qrcheckin.session.SessionStatus;

/**
 * AI 도우미 조회 도구의 결과. 모두 로그인한 학생 본인 기준이며 이름·학번·이메일은 담지 않는다
 * (LLM 에 보내는 값이라 필요한 집계만).
 */
public final class AssistantDtos {

    private AssistantDtos() {
    }

    /** 답변 근거로 보여 줄 서비스 출결 규칙 */
    public record Policy(double failRatio, int latesPerAbsence, String description) {
    }

    // ---- getMyCourses ----

    public record CourseItem(
            Long courseId,
            String title,
            String semesterKey,
            String semesterLabel,
            /** 오늘 기준 현재 학기 수업인지(학기 미정이면 true 로 본다) */
            boolean currentSemester,
            boolean finalized,
            List<ScheduleSlot> schedules) {
    }

    public record CoursesResult(LocalDateTime asOf, String currentSemesterLabel, List<CourseItem> courses) {
    }

    // ---- getMyAttendanceSummary ----

    public record SummaryResult(LocalDateTime asOf, Policy policy, List<CourseSummary> courses) {
    }

    // ---- getMyUpcomingSessions ----

    public record UpcomingSession(
            Long courseId,
            String courseTitle,
            Long sessionId,
            int sessionNo,
            LocalDateTime startAt,
            /** 주간 시간표에서 찾은 종료 시각(시간표와 맞지 않는 회차면 null) */
            LocalDateTime endAt,
            SessionStatus sessionStatus) {
    }

    public record UpcomingResult(LocalDateTime asOf, LocalDate from, LocalDate to, List<UpcomingSession> sessions) {
    }

    // ---- simulateMyAttendance ----

    public record SimulationResult(
            LocalDateTime asOf,
            Policy policy,
            Long courseId,
            String courseTitle,
            AttendanceSimulator.Result result,
            /** 이 계산은 가정이며 기록을 바꾸지 않는다는 안내 */
            String note) {
    }

    // ---- getMyRequestOptions ----

    public record RequestOptionItem(
            Long sessionId,
            int sessionNo,
            LocalDateTime scheduledAt,
            SessionStatus sessionStatus,
            AttendanceStatus myStatus,
            boolean pending,
            boolean canRequest,
            /** 신청할 수 없을 때 이유(가능하면 null) */
            String blockedReason,
            /** 신청 화면 바로가기(신청할 수 있을 때만) */
            String requestPath) {
    }

    public record RequestOptionsResult(
            LocalDateTime asOf,
            Long courseId,
            String courseTitle,
            boolean finalized,
            List<RequestOptionItem> sessions) {
    }
}
