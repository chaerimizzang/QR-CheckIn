package com.qrcheckin.push;

import java.time.LocalDateTime;

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
import jakarta.persistence.UniqueConstraint;

/** 휴대폰·브라우저 하나의 알림 구독. 한 사람이 여러 기기에서 구독할 수 있다 */
@Entity
@Table(name = "push_subscription",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_endpoint", columnNames = "endpoint"))
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 브라우저 회사(구글·애플·모질라) 푸시 서버 주소 */
    @Column(nullable = false, length = 512)
    private String endpoint;

    @Column(nullable = false, length = 120)
    private String p256dh;

    @Column(nullable = false, length = 60)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PushSubscription() {
    }

    public PushSubscription(User user, String endpoint, String p256dh, String auth, LocalDateTime createdAt) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = createdAt;
    }

    /** 같은 기기를 다른 계정으로 로그인해 구독하면 주인을 바꾼다 */
    public void reassign(User user, String p256dh, String auth) {
        this.user = user;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
}
