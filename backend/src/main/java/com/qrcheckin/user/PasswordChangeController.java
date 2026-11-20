
package com.qrcheckin.user;

import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;

import com.qrcheckin.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PasswordChangeController {

    private final PasswordChangeService passwordChangeService;

    public PasswordChangeController(
            PasswordChangeService passwordChangeService) {
        this.passwordChangeService = passwordChangeService;
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank
            @Size(min = 8, max = 64, message = "새 비밀번호는 8~64자여야 해요") String newPassword,
            @NotBlank String confirmPassword
    ) {}

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody ChangePasswordRequest request) {

        // 오류는 ApiException 으로 던져야 500 이 아니라 400 + 안내 문구로 응답된다
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "새 비밀번호 두 칸이 서로 달라요");
        }

        passwordChangeService.changePassword(
                user.id(),
                request.currentPassword(),
                request.newPassword()
        );
    }
}
