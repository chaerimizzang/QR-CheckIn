package com.qrcheckin.profile;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qrcheckin.auth.AuthUser;

/** 프로필 — 학생: 수강 중인 교육별 출석률 / 관리자: 개설한 교육 요약 */
@RestController
@RequestMapping("/api/v1/me")
public class StudentProfileController {

    private final StudentProfileService profileService;

    public StudentProfileController(StudentProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentProfileResponse profile(@AuthenticationPrincipal AuthUser student) {
        return profileService.myProfile(student);
    }

    @GetMapping("/admin-profile")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminProfileResponse adminProfile(@AuthenticationPrincipal AuthUser admin) {
        return profileService.adminProfile(admin);
    }
}
