package com.qrcheckin.schedule;

import java.time.DayOfWeek;
import java.time.LocalTime;

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

/** 교육의 주간 시간표 한 칸 — 예: 월요일 09:00 부터 120분. 한 교육에 여러 칸 가능 */
@Entity
@Table(name = "course_schedule")
public class CourseSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    protected CourseSchedule() {
    }

    public CourseSchedule(Course course, DayOfWeek dayOfWeek, LocalTime startTime, int durationMinutes) {
        this.course = course;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
    }

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public LocalTime getEndTime() { return startTime.plusMinutes(durationMinutes); }
}
