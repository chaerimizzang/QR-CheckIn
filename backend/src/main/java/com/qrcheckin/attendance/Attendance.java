package com.qrcheckin.attendance;

import java.time.LocalDateTime;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 출결. UNIQUE(session_id, user_id) 로 이중 출석을 DB 수준에서 막는다. */
@Entity
@Table(name = "attendance",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_attendance_session_user", columnNames = {"session_id", "user_id"}),
                // D-12: 한 회차에 같은 기기로는 한 명만 QR 출석. 수동·자동 처리 기록은 device_id 가 NULL 이라 제한 없음
                @UniqueConstraint(name = "uk_attendance_session_device", columnNames = {"session_id", "device_id"})
        })
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttendanceStatus status;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttendanceSource source;

    /** D-12 QR 출석에 쓴 기기 번호(QR 출석일 때만) */
    @Column(name = "device_id", length = 64)
    private String deviceId;

    protected Attendance() {
    }

    private Attendance(ClassSession session, User user, AttendanceStatus status,
                       LocalDateTime checkedAt, AttendanceSource source) {
        this.session = session;
        this.user = user;
        this.status = status;
        this.checkedAt = checkedAt;
        this.source = source;
    }

    public static Attendance byQr(ClassSession session, User user, LocalDateTime now, String deviceId) {
        Attendance a = new Attendance(session, user, AttendanceStatus.PRESENT, now, AttendanceSource.QR);
        a.deviceId = deviceId;
        return a;
    }

    public static Attendance absentBySystem(ClassSession session, User user, LocalDateTime now) {
        return new Attendance(session, user, AttendanceStatus.ABSENT, now, AttendanceSource.SYSTEM);
    }

    public static Attendance manual(ClassSession session, User user, AttendanceStatus status, LocalDateTime now) {
        return new Attendance(session, user, status, now, AttendanceSource.MANUAL);
    }

    /** 관리자 수정(B-12): 어떤 상태에서 어떤 상태로든 변경 가능 */
    public void changeByAdmin(AttendanceStatus newStatus, LocalDateTime now) {
        this.status = newStatus;
        this.source = AttendanceSource.MANUAL;
        this.checkedAt = now;
    }

    public Long getId() { return id; }
    public ClassSession getSession() { return session; }
    public User getUser() { return user; }
    public AttendanceStatus getStatus() { return status; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public String getDeviceId() { return deviceId; }
    public AttendanceSource getSource() { return source; }
}
