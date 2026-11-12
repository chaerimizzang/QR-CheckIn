
package com.qrcheckin.course;

import com.qrcheckin.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "student_contact")
public class StudentContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "phone_number", length = 20, nullable = false)
    private String phoneNumber;

    protected StudentContact() {
    }

    public StudentContact(User user, String phoneNumber) {
        this.user = user;
        this.phoneNumber = phoneNumber;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void updatePhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}

