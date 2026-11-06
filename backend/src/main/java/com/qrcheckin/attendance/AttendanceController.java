package com.qrcheckin.attendance;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.qrcheckin.attendance.AttendanceDtos.ChangeStatusRequest;
import com.qrcheckin.attendance.AttendanceDtos.MyAttendanceResponse;
import com.qrcheckin.attendance.AttendanceDtos.RosterResponse;
import com.qrcheckin.attendance.AttendanceDtos.RosterRow;
import com.qrcheckin.auth.AuthUser;

import jakarta.validation.Valid;

/** B-10 본인 출결, B-11 회차 현황, B-12 출결 수정 */
@RestController
@RequestMapping("/api/v1")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/sessions/{sessionId}/attendances")
    @PreAuthorize("hasRole('ADMIN')")
    public RosterResponse roster(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user) {
        return attendanceService.roster(sessionId, user);
    }

    /**
     * 계획서의 PATCH /attendances/{attendanceId} 대신 (회차, 학생)으로 지정한다.
     * 아직 기록이 없는 학생(출석 진행 중 미출석자)도 바로 처리할 수 있게 하기 위해서다.
     */
    @PutMapping("/sessions/{sessionId}/attendances/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public RosterRow change(@PathVariable Long sessionId, @PathVariable Long userId,
                            @Valid @RequestBody ChangeStatusRequest req, @AuthenticationPrincipal AuthUser user) {
        return attendanceService.changeStatus(sessionId, userId, req.status(), user);
    }

    @GetMapping("/me/attendances")
    @PreAuthorize("hasRole('STUDENT')")
    public MyAttendanceResponse mine(@RequestParam Long courseId, @AuthenticationPrincipal AuthUser user) {
        return attendanceService.myAttendances(courseId, user);
    }
}
