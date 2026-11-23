package com.qrcheckin.session;

import java.time.LocalDateTime;

import com.qrcheckin.course.Course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 회차(한 번의 수업). 출석 조건(장소·반경·QR 출석 시간)을 회차에 직접 저장한다(계획서 6.2).
 * closedAt = openedAt + checkinMinutes. [출석 종료]를 누르면 그 시각으로 앞당겨진다.
 */
@Entity
@Table(name = "class_session")
public class ClassSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "session_no", nullable = false)
    private int sessionNo;

    /** 수업 예정 일시(B-03) */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    private Double latitude;

    private Double longitude;

    @Column(name = "radius_m", nullable = false)
    private int radiusM;

    @Column(name = "checkin_minutes", nullable = false)
    private int checkinMinutes;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** 회차별 HMAC salt — 회차를 열 때마다 새로 만든다 */
    @Column(name = "qr_salt", length = 64)
    private String qrSalt;

    /** 수업 시작 전 알림을 보낸 시각(안 보냈으면 null) */
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SessionStatus status;

    /** 휴강 사유(선택)와 휴강으로 바꾼 시각 */
    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    protected ClassSession() {
    }

    public ClassSession(Course course, int sessionNo, LocalDateTime scheduledAt,
                        Double latitude, Double longitude, int radiusM, int checkinMinutes) {
        this.course = course;
        this.sessionNo = sessionNo;
        this.scheduledAt = scheduledAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusM = radiusM;
        this.checkinMinutes = checkinMinutes;
        this.status = SessionStatus.SCHEDULED;
    }

    /** 출석 조건 수정(B-05) — SCHEDULED 상태에서만 서비스가 호출한다 */
    public void updateConditions(LocalDateTime scheduledAt, Double latitude, Double longitude,
                                 int radiusM, int checkinMinutes) {
        if (!scheduledAt.equals(this.scheduledAt)) {
            this.reminderSentAt = null; // 시간이 바뀌면 새 시간 기준으로 다시 알린다
        }
        this.scheduledAt = scheduledAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusM = radiusM;
        this.checkinMinutes = checkinMinutes;
    }

    /** 수업 시작 전 알림을 보냈다고 표시(같은 회차에 두 번 보내지 않도록) */
    public void markReminded(LocalDateTime now) {
        this.reminderSentAt = now;
    }

    public void open(LocalDateTime now, String newSalt) {
        this.openedAt = now;
        this.closedAt = now.plusMinutes(checkinMinutes);
        this.qrSalt = newSalt;
        this.status = SessionStatus.OPEN;
    }

    public void close(LocalDateTime now) {
        if (status == SessionStatus.OPEN && (closedAt == null || now.isBefore(closedAt))) {
            this.closedAt = now;
        }
        this.status = SessionStatus.CLOSED;
    }

    /** 휴강: SCHEDULED 회차만(서비스에서 확인) */
    public void cancel(String reason, LocalDateTime now) {
        this.status = SessionStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledAt = now;
    }

    /** 휴강 취소: 다시 예정 회차로. 수업 전 알림도 다시 보낼 수 있게 한다 */
    public void uncancel() {
        this.status = SessionStatus.SCHEDULED;
        this.cancelReason = null;
        this.canceledAt = null;
        this.reminderSentAt = null;
    }

    public boolean isCanceled() {
        return status == SessionStatus.CANCELED;
    }

    /** 전체 회차(F 기준 분모)에 들어가는 회차인지 — 휴강은 빠진다 */
    public boolean countsAsPlanned() {
        return status != SessionStatus.CANCELED;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public int getSessionNo() { return sessionNo; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public int getRadiusM() { return radiusM; }
    public int getCheckinMinutes() { return checkinMinutes; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public String getCancelReason() { return cancelReason; }
    public LocalDateTime getCanceledAt() { return canceledAt; }
    public String getQrSalt() { return qrSalt; }
    public SessionStatus getStatus() { return status; }
}
