package com.qrcheckin.course;

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

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

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

    public boolean isOwnedBy(Long userId) {
        return owner.getId().equals(userId);
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getTitle() { return title; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}
