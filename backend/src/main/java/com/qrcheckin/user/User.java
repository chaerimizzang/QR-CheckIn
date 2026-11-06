package com.qrcheckin.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "student_no", length = 30)
    private String studentNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected User() {
    }

    public User(String email, String passwordHash, String name, String studentNo, Role role, LocalDateTime createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.studentNo = studentNo;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public String getStudentNo() { return studentNo; }
    public Role getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
