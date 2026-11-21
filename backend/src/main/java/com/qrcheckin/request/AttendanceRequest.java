package com.qrcheckin.request;

import java.time.LocalDateTime;

import com.qrcheckin.attendance.AttendanceStatus;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 출석 인정 신청(공결·정정). 학생이 내고 교수가 승인·반려한다 */
@Entity
@Table(name = "attendance_request", indexes = {
        @Index(name = "idx_request_session_student", columnList = "session_id, student_id"),
        @Index(name = "idx_request_status", columnList = "status")
})
public class AttendanceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private RequestType type;

    /** 승인되면 바뀔 상태(공결이면 EXCUSED) */
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_status", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private AttendanceStatus requestedStatus;

    /** 신청할 때의 출결 상태(기록이 없었으면 null) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_at_request", length = 20, columnDefinition = "varchar(20)")
    private AttendanceStatus statusAtRequest;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "has_evidence", nullable = false)
    private boolean hasEvidence;

    @Column(name = "review_comment", length = 300)
    private String reviewComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AttendanceRequest() {
    }

    public AttendanceRequest(ClassSession session, User student, RequestType type, AttendanceStatus requestedStatus,
                             AttendanceStatus statusAtRequest, String reason, boolean hasEvidence, LocalDateTime now) {
        this.session = session;
        this.student = student;
        this.type = type;
        this.requestedStatus = requestedStatus;
        this.statusAtRequest = statusAtRequest;
        this.reason = reason;
        this.hasEvidence = hasEvidence;
        this.createdAt = now;
    }

    public void approve(User reviewer, String comment, LocalDateTime now) {
        review(RequestStatus.APPROVED, reviewer, comment, now);
    }

    public void reject(User reviewer, String comment, LocalDateTime now) {
        review(RequestStatus.REJECTED, reviewer, comment, now);
    }

    public void cancel(LocalDateTime now) {
        this.status = RequestStatus.CANCELED;
        this.reviewedAt = now;
    }

    private void review(RequestStatus result, User reviewer, String comment, LocalDateTime now) {
        this.status = result;
        this.reviewedBy = reviewer;
        this.reviewComment = comment;
        this.reviewedAt = now;
    }

    public Long getId() { return id; }
    public ClassSession getSession() { return session; }
    public User getStudent() { return student; }
    public RequestType getType() { return type; }
    public AttendanceStatus getRequestedStatus() { return requestedStatus; }
    public AttendanceStatus getStatusAtRequest() { return statusAtRequest; }
    public String getReason() { return reason; }
    public RequestStatus getStatus() { return status; }
    public boolean isHasEvidence() { return hasEvidence; }
    public String getReviewComment() { return reviewComment; }
    public User getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
