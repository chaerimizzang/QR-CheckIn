package com.qrcheckin.attendance;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 학생·교육별로 마지막으로 알린 결석 경고 단계(같은 경고를 여러 번 보내지 않도록) */
@Entity
@Table(name = "absence_warning_state",
        uniqueConstraints = @UniqueConstraint(name = "uk_warning_course_user", columnNames = {"course_id", "user_id"}))
public class AbsenceWarningState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private AbsenceRule.Level level;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AbsenceWarningState() {
    }

    public AbsenceWarningState(Long courseId, Long userId, AbsenceRule.Level level, LocalDateTime now) {
        this.courseId = courseId;
        this.userId = userId;
        this.level = level;
        this.updatedAt = now;
    }

    public void update(AbsenceRule.Level level, LocalDateTime now) {
        this.level = level;
        this.updatedAt = now;
    }

    public Long getCourseId() { return courseId; }
    public Long getUserId() { return userId; }
    public AbsenceRule.Level getLevel() { return level; }
}
