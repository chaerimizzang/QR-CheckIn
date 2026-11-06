package com.qrcheckin.course;

import com.qrcheckin.user.User;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "enrollment",
        uniqueConstraints = @UniqueConstraint(name = "uk_enrollment_course_user", columnNames = {"course_id", "user_id"}))
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected Enrollment() {
    }

    public Enrollment(Course course, User user) {
        this.course = course;
        this.user = user;
    }

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public User getUser() { return user; }
}
