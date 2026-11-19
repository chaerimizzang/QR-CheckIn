
package com.qrcheckin.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordChangeRepository passwordChangeRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordChangeService(
            UserRepository userRepository,
            PasswordChangeRepository passwordChangeRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordChangeRepository = passwordChangeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(
            Long userId,
            String currentPassword,
            String newPassword) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(
                currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException(
                    "현재 비밀번호가 일치하지 않습니다.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException(
                    "새 비밀번호는 8자 이상이어야 합니다.");
        }

        if (passwordEncoder.matches(
                newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException(
                    "기존 비밀번호와 다른 비밀번호를 입력해 주세요.");
        }

        String encodedPassword = passwordEncoder.encode(newPassword);

        int updated = passwordChangeRepository.updatePassword(
                userId, encodedPassword);

        if (updated != 1) {
            throw new IllegalStateException(
                    "비밀번호 변경에 실패했습니다.");
        }
    }
}
