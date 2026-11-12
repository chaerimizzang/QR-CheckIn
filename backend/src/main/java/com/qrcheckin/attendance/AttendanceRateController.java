
package com.qrcheckin.attendance;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qrcheckin.auth.AuthUser;

@RestController
@RequestMapping("/api/v1/courses")
public class AttendanceRateController {

    private final AttendanceRateService attendanceRateService;

    public AttendanceRateController(
            AttendanceRateService attendanceRateService) {
        this.attendanceRateService = attendanceRateService;
    }

    @GetMapping("/{courseId}/attendance/rate")
    @PreAuthorize("hasRole('ADMIN')")
    public AttendanceRateResponse getAttendanceRate(
            @PathVariable Long courseId,
            @AuthenticationPrincipal AuthUser admin) {

        return attendanceRateService.calculate(courseId, admin);
    }
}
