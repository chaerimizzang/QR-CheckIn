package com.qrcheckin.checkin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.checkin.CheckinDtos.CheckinRequest;
import com.qrcheckin.checkin.CheckinDtos.CheckinResponse;
import com.qrcheckin.checkin.CheckinDtos.QrResponse;
import com.qrcheckin.session.SessionDtos.SessionResponse;

import jakarta.validation.Valid;

/** B-06 출석 진행(관리자), B-07 출석 인증(수강생) */
@RestController
@RequestMapping("/api/v1")
public class CheckinController {

    private final SessionRunService sessionRunService;
    private final CheckinService checkinService;

    public CheckinController(SessionRunService sessionRunService, CheckinService checkinService) {
        this.sessionRunService = sessionRunService;
        this.checkinService = checkinService;
    }

    @PostMapping("/sessions/{sessionId}/open")
    @PreAuthorize("hasRole('ADMIN')")
    public SessionResponse open(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user) {
        return sessionRunService.open(sessionId, user);
    }

    @PostMapping("/sessions/{sessionId}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public SessionResponse close(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user) {
        return sessionRunService.close(sessionId, user);
    }

    @GetMapping("/sessions/{sessionId}/qr")
    @PreAuthorize("hasRole('ADMIN')")
    public QrResponse qr(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user) {
        return sessionRunService.currentQr(sessionId, user);
    }

    @PostMapping("/check-in")
    @PreAuthorize("hasRole('STUDENT')")
    public CheckinResponse checkIn(@Valid @RequestBody CheckinRequest req, @AuthenticationPrincipal AuthUser user) {
        return checkinService.checkIn(user, req);
    }
}
