package com.qrcheckin.course;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.course.CourseDtos.CourseResponse;
import com.qrcheckin.course.CourseDtos.FinalizeCheck;
import com.qrcheckin.course.CourseDtos.SemesterResponse;

import io.swagger.v3.oas.annotations.Operation;

/** 학기 · 출결 확정 */
@RestController
@RequestMapping("/api/v1")
public class SemesterController {

    private final CourseFinalizeService service;

    public SemesterController(CourseFinalizeService service) {
        this.service = service;
    }

    @Operation(summary = "학기 선택 목록(현재 학기 + 내 교육이 있는 학기, 최신순)")
    @GetMapping("/semesters")
    public List<SemesterResponse> semesters(@AuthenticationPrincipal AuthUser user) {
        return service.mySemesters(user);
    }

    @Operation(summary = "오늘 기준 현재 학기")
    @GetMapping("/semesters/current")
    public SemesterResponse current() {
        return service.current();
    }

    @Operation(summary = "출결 확정 전 확인(진행 중 회차·대기 신청·지워질 예정 회차 수)")
    @GetMapping("/courses/{courseId}/finalize-check")
    @PreAuthorize("hasRole('ADMIN')")
    public FinalizeCheck check(@PathVariable Long courseId, @AuthenticationPrincipal AuthUser user) {
        return service.check(courseId, user);
    }

    @Operation(summary = "출결 확정 — 이후 읽기 전용, 열지 않은 예정 회차는 삭제")
    @PostMapping("/courses/{courseId}/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    public CourseResponse finalizeCourse(@PathVariable Long courseId, @AuthenticationPrincipal AuthUser user) {
        return service.finalizeCourse(courseId, user);
    }

    @Operation(summary = "출결 확정 취소")
    @PostMapping("/courses/{courseId}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public CourseResponse reopen(@PathVariable Long courseId, @AuthenticationPrincipal AuthUser user) {
        return service.reopen(courseId, user);
    }
}
