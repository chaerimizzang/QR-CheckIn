package com.qrcheckin.auth;

import com.qrcheckin.user.Role;
import com.qrcheckin.user.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 해요") String password,
            @NotBlank @Size(max = 50) String name,
            @Size(max = 30) String studentNo,
            @NotNull Role role) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record UserResponse(Long id, String email, String name, String studentNo, Role role) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getName(), u.getStudentNo(), u.getRole());
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }
}
