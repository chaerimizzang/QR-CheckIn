package com.qrcheckin.request;

import java.time.LocalDateTime;

import com.qrcheckin.attendance.AttendanceStatus;
import com.qrcheckin.session.SessionStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 출석 인정 신청 요청·응답 */
public final class RequestDtos {

    private RequestDtos() {
    }

    /** 신청 한 건(학생·교수 화면 공통) */
    public record RequestRow(
            Long id,
            RequestType type,
            RequestStatus status,
            AttendanceStatus requestedStatus,
            AttendanceStatus statusAtRequest,
            String reason,
            boolean hasEvidence,
            LocalDateTime createdAt,
            String reviewComment,
            String reviewerName,
            LocalDateTime reviewedAt,
            Long courseId,
            String courseTitle,
            Long sessionId,
            int sessionNo,
            LocalDateTime scheduledAt,
            SessionStatus sessionStatus,
            Long studentId,
            String studentName,
            String studentNo) {

        static RequestRow from(AttendanceRequest r) {
            var s = r.getSession();
            var u = r.getStudent();
            return new RequestRow(r.getId(), r.getType(), r.getStatus(), r.getRequestedStatus(), r.getStatusAtRequest(),
                    r.getReason(), r.isHasEvidence(), r.getCreatedAt(), r.getReviewComment(),
                    r.getReviewedBy() == null ? null : r.getReviewedBy().getName(), r.getReviewedAt(),
                    s.getCourse().getId(), s.getCourse().getTitle(), s.getId(), s.getSessionNo(), s.getScheduledAt(),
                    s.getStatus(), u.getId(), u.getName(), u.getStudentNo());
        }
    }

    /** 교수의 인정·반려(반려는 사유 필수 — 서비스에서 검사) */
    public record ReviewRequest(@Size(max = 300, message = "의견은 300자 이내로 입력해 주세요") String comment) {
    }

    public record PendingCount(long pending) {
    }

    /** 신청 화면에서 고를 수 있는 회차(학생 본인 기준) */
    public record SessionOption(
            Long sessionId,
            int sessionNo,
            LocalDateTime scheduledAt,
            SessionStatus sessionStatus,
            AttendanceStatus myStatus,
            boolean pending) {
    }

    /** 신청 폼 값(multipart 의 글자 항목) */
    public record SubmitForm(
            RequestType type,
            AttendanceStatus requestedStatus,
            @NotBlank(message = "사유를 입력해 주세요") @Size(max = 500, message = "사유는 500자 이내로 입력해 주세요")
            String reason) {
    }
}
