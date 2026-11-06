package com.qrcheckin.attendance;

import java.time.LocalDateTime;
import java.util.List;

import com.qrcheckin.session.SessionStatus;

import jakarta.validation.constraints.NotNull;

public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    /** B-11 회차 명단의 한 줄. 아직 기록이 없으면 status·checkedAt·source 가 null */
    public record RosterRow(Long userId, String name, String studentNo, String email,
                            Long attendanceId, AttendanceStatus status, LocalDateTime checkedAt,
                            AttendanceSource source) {
    }

    public record Summary(long enrolled, long present, long late, long absent, long pending) {
    }

    public record RosterResponse(Long sessionId, int sessionNo, SessionStatus sessionStatus,
                                 LocalDateTime openedAt, LocalDateTime closedAt,
                                 Summary summary, List<RosterRow> students) {
    }

    /** B-12 관리자 출결 수정 */
    public record ChangeStatusRequest(@NotNull AttendanceStatus status) {
    }

    /** B-10 본인 출결: 회차마다 한 줄. 출결이 아직 정해지지 않은 회차는 status 가 null */
    public record MyRow(Long sessionId, int sessionNo, LocalDateTime scheduledAt, SessionStatus sessionStatus,
                        AttendanceStatus status, LocalDateTime checkedAt) {
    }

    public record MyAttendanceResponse(Long courseId, String courseTitle, List<MyRow> sessions) {
    }
}
