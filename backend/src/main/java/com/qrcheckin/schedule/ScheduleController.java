package com.qrcheckin.schedule;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.schedule.ScheduleDtos.GenerateRequest;
import com.qrcheckin.schedule.ScheduleDtos.GenerateResult;
import com.qrcheckin.schedule.ScheduleDtos.ScheduleRequest;
import com.qrcheckin.schedule.ScheduleDtos.ScheduleSlot;

import jakarta.validation.Valid;

/** 교육 주간 시간표 + 시간표로 회차 자동 생성 */
@RestController
@RequestMapping("/api/v1/courses/{courseId}")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/schedules")
    public List<ScheduleSlot> list(@PathVariable Long courseId, @AuthenticationPrincipal AuthUser user) {
        return scheduleService.list(courseId, user);
    }

    @PutMapping("/schedules")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ScheduleSlot> replace(@PathVariable Long courseId, @Valid @RequestBody ScheduleRequest req,
                                      @AuthenticationPrincipal AuthUser admin) {
        return scheduleService.replace(courseId, req, admin);
    }

    @PostMapping("/sessions/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public GenerateResult generate(@PathVariable Long courseId, @RequestBody(required = false) GenerateRequest req,
                                   @AuthenticationPrincipal AuthUser admin) {
        return scheduleService.generate(courseId, req, admin);
    }
}
