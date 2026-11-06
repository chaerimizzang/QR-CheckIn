package com.qrcheckin.session;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.session.SessionDtos.SessionRequest;
import com.qrcheckin.session.SessionDtos.SessionResponse;

import jakarta.validation.Valid;

/** B-03 회차, B-05 출석 조건 — 모두 관리자 전용 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ADMIN')")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/courses/{courseId}/sessions")
    public List<SessionResponse> list(@PathVariable Long courseId, @AuthenticationPrincipal AuthUser user) {
        return sessionService.list(courseId, user);
    }

    @PostMapping("/courses/{courseId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@PathVariable Long courseId, @Valid @RequestBody SessionRequest req,
                                  @AuthenticationPrincipal AuthUser user) {
        return sessionService.create(courseId, req, user);
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionResponse get(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user) {
        return sessionService.get(sessionId, user);
    }

    @PutMapping("/sessions/{sessionId}")
    public SessionResponse update(@PathVariable Long sessionId, @Valid @RequestBody SessionRequest req,
                                  @AuthenticationPrincipal AuthUser user) {
        return sessionService.update(sessionId, req, user);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user) {
        sessionService.delete(sessionId, user);
    }
}
