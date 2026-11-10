package com.qrcheckin.attendance;
import java.time.LocalDateTime;
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

@Entity
@Table(name = "attendance_history")
public class AttendanceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) //The attendance record that was changed
    @JoinColumn(name = "attendance_id", nullable = false)
    private Attendance attendance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) //The admin who made the change
    @JoinColumn(name = "modified_by", nullable = false)
    private User modifiedBy;

    @Enumerated(EnumType.STRING)   //Previous status
    @Column(name = "old_status", length = 10)
    private AttendanceStatus oldStatus;

    @Enumerated(EnumType.STRING) //New status
    @Column(name = "new_status", nullable = false, length = 10)
    private AttendanceStatus newStatus;

    @Column(name = "modified_at", nullable = false) // Date and time of modification
    private LocalDateTime modifiedAt;

    protected AttendanceHistory() {}

    public AttendanceHistory(
            Attendance attendance,
            User modifiedBy,
            AttendanceStatus oldStatus,
            AttendanceStatus newStatus,
            LocalDateTime modifiedAt) {
        this.attendance = attendance;
        this.modifiedBy = modifiedBy;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.modifiedAt = modifiedAt;
    }

    public Long getId() {return id;}
    public Attendance getAttendance() {return attendance;}
    public User getModifiedBy() {return modifiedBy;}
    public AttendanceStatus getOldStatus() {return oldStatus;}
    public AttendanceStatus getNewStatus() {return newStatus;}
    public LocalDateTime getModifiedAt() {return modifiedAt;}
}
