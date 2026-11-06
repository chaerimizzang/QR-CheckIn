package com.qrcheckin.auth;

import com.qrcheckin.user.Role;

/** JWT 에서 꺼낸 로그인 사용자. 컨트롤러에서 @AuthenticationPrincipal AuthUser 로 받는다. */
public record AuthUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
