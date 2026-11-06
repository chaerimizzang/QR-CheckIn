package com.qrcheckin.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.qrcheckin.auth.AuthDtos.LoginRequest;
import com.qrcheckin.auth.AuthDtos.SignupRequest;
import com.qrcheckin.auth.AuthDtos.TokenResponse;
import com.qrcheckin.auth.AuthDtos.UserResponse;

import jakarta.validation.Valid;

/** B-01 회원가입·로그인 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest req) {
        return authService.signup(req);
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /** 프론트에서 새로고침 후 로그인 상태 확인용 */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return authService.me(user.id());
    }
}
