package com.qrcheckin.request;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.qrcheckin.attendance.AttendanceStatus;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.request.RequestDtos.PendingCount;
import com.qrcheckin.request.RequestDtos.RequestRow;
import com.qrcheckin.request.RequestDtos.ReviewRequest;
import com.qrcheckin.request.RequestDtos.SessionOption;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

/** 출석 인정(공결·정정 신청) */
@RestController
@RequestMapping("/api/v1")
public class AttendanceRequestController {

    private final AttendanceRequestService service;
    private final RequestNotifier notifier;

    public AttendanceRequestController(AttendanceRequestService service, RequestNotifier notifier) {
        this.service = service;
        this.notifier = notifier;
    }

    // ---- 학생 ----

    @Operation(summary = "신청할 수 있는 회차(내 출결 상태 포함)")
    @GetMapping("/courses/{courseId}/request-options")
    @PreAuthorize("hasRole('STUDENT')")
    public List<SessionOption> options(@PathVariable Long courseId, @AuthenticationPrincipal AuthUser user) {
        return service.options(courseId, user);
    }

    @Operation(summary = "출석 인정 신청(사유 + 증빙 파일 선택)",
            description = "multipart: type=EXCUSE|CORRECTION, requestedStatus=PRESENT|LATE(정정일 때), reason, file(선택, 5MB)")
    @PostMapping(value = "/sessions/{sessionId}/requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public RequestRow submit(@PathVariable Long sessionId,
                             @RequestParam RequestType type,
                             @RequestParam(required = false) AttendanceStatus requestedStatus,
                             @RequestParam String reason,
                             @RequestParam(required = false) MultipartFile file,
                             @AuthenticationPrincipal AuthUser user) {
        RequestRow row = service.submit(sessionId, user, type, requestedStatus, reason, file);
        notifier.submitted(row, professorOf(row));
        return row;
    }

    @Operation(summary = "내 신청 목록")
    @GetMapping("/me/requests")
    @PreAuthorize("hasRole('STUDENT')")
    public List<RequestRow> mine(@AuthenticationPrincipal AuthUser user) {
        return service.mine(user);
    }

    @Operation(summary = "신청 취소(처리 전에만)")
    @PostMapping("/requests/{id}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public RequestRow cancel(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return service.cancel(id, user);
    }

    // ---- 교수 ----

    @Operation(summary = "내 교육들에 들어온 신청 목록")
    @GetMapping("/requests")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RequestRow> forProfessor(@AuthenticationPrincipal AuthUser user) {
        return service.forProfessor(user);
    }

    @Operation(summary = "처리 대기 중인 신청 수(메뉴 표시용)")
    @GetMapping("/requests/pending-count")
    @PreAuthorize("hasRole('ADMIN')")
    public PendingCount pendingCount(@AuthenticationPrincipal AuthUser user) {
        return new PendingCount(service.pendingCount(user));
    }

    @Operation(summary = "승인 — 출결이 신청한 상태로 바뀌고 수정 이력이 남는다")
    @PostMapping("/requests/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public RequestRow approve(@PathVariable Long id, @Valid @RequestBody(required = false) ReviewRequest req,
                              @AuthenticationPrincipal AuthUser user) {
        RequestRow row = service.approve(id, user, req == null ? null : req.comment());
        notifier.reviewed(row);
        return row;
    }

    @Operation(summary = "반려(사유 필수)")
    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public RequestRow reject(@PathVariable Long id, @Valid @RequestBody(required = false) ReviewRequest req,
                             @AuthenticationPrincipal AuthUser user) {
        RequestRow row = service.reject(id, user, req == null ? null : req.comment());
        notifier.reviewed(row);
        return row;
    }

    // ---- 공통 ----

    @Operation(summary = "증빙 파일 보기(신청한 학생·담당 교수만)")
    @GetMapping("/requests/{id}/evidence")
    public ResponseEntity<byte[]> evidence(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        RequestEvidence e = service.evidence(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(e.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(e.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(e.getData());
    }

    private Long professorOf(RequestRow row) {
        return service.professorOfCourse(row.courseId());
    }
}
