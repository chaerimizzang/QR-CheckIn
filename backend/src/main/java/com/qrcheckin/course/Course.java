package com.qrcheckin.course;

import java.time.LocalDateTime;

import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.LocalDate;

import com.qrcheckin.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 교육(강의). owner 는 개설한 관리자 — 소유권 검사에 쓴다. */
@Entity
@Table(name = "course")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String title;

    /** 강의실(선택, 예: 공학관 302호) — 화면 표시용 */
    @Column(length = 50)
    private String room;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** 학기(예: 2026년 2학기). 예전 교육은 시작 시 자동으로 채운다(SemesterBackfill) */
    @Column(name = "semester_year")
    private Integer semesterYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "semester_term", length = 20, columnDefinition = "varchar(20)")
    private SemesterTerm semesterTerm;

    /** 출결 확정 시각. 확정된 교육은 읽기 전용(출결·회차·수강생을 바꿀 수 없다) */
    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    protected Course() {
    }

    public Course(User owner, String title, LocalDate startDate, LocalDate endDate) {
        this.owner = owner;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void update(String title, LocalDate startDate, LocalDate endDate) {
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getRoom() { return room; }

    /** 강의실 — 비우면 null */
    public void changeRoom(String room) {
        this.room = (room == null || room.isBlank()) ? null : room.trim();
    }

    public void setSemester(Semester semester) {
        this.semesterYear = semester.year();
        this.semesterTerm = semester.term();
    }

    /** 학기가 비어 있으면 null */
    public Semester getSemester() {
        return semesterYear == null || semesterTerm == null ? null : new Semester(semesterYear, semesterTerm);
    }

    public void finalizeAttendance(LocalDateTime now) {
        this.finalizedAt = now;
    }

    public void reopen() {
        this.finalizedAt = null;
    }

    public boolean isFinalized() {
        return finalizedAt != null;
    }

    /** 출결이 확정된 교육이면 바꾸는 요청을 막는다(조회·내려받기는 된다) */
    public void requireEditable() {
        if (finalizedAt != null) {
            throw new ApiException(ErrorCode.COURSE_FINALIZED);
        }
    }

    public boolean isOwnedBy(Long userId) {
        return owner.getId().equals(userId);
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getTitle() { return title; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public LocalDateTime getFinalizedAt() { return finalizedAt; }
}
