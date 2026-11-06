package com.qrcheckin.auth;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.auth.AuthDtos.LoginRequest;
import com.qrcheckin.auth.AuthDtos.SignupRequest;
import com.qrcheckin.auth.AuthDtos.TokenResponse;
import com.qrcheckin.auth.AuthDtos.UserResponse;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.user.User;
import com.qrcheckin.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final Clock clock;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.clock = clock;
    }

    @Transactional
    public UserResponse signup(SignupRequest req) {
        String email = normalize(req.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_DUPLICATED);
        }
        User user = new User(email, passwordEncoder.encode(req.password()), req.name().trim(),
                blankToNull(req.studentNo()), req.role(), LocalDateTime.now(clock));
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) { // 동시 가입 경쟁
            throw new ApiException(ErrorCode.EMAIL_DUPLICATED);
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(normalize(req.email()))
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(ErrorCode.LOGIN_FAILED));
        return new TokenResponse(jwtProvider.issue(user), "Bearer", jwtProvider.expiresInSeconds(),
                UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return userRepository.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
