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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SessionStatus status;

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
        this.scheduledAt = scheduledAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusM = radiusM;
        this.checkinMinutes = checkinMinutes;
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
    public String getQrSalt() { return qrSalt; }
    public SessionStatus getStatus() { return status; }
}
