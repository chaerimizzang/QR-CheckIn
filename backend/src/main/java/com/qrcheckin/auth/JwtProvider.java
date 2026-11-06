package com.qrcheckin.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.qrcheckin.user.Role;
import com.qrcheckin.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Access 토큰만 사용(계획서 9.2). Refresh 토큰은 심화 기능 D-11. */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final Duration expiration;

    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-hours}") long expirationHours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 은 32바이트 이상이어야 합니다");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiration = Duration.ofHours(expirationHours);
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public long expiresInSeconds() {
        return expiration.toSeconds();
    }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
