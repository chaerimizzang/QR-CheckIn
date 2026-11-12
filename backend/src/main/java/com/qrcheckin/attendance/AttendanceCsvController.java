
package com.qrcheckin.attendance;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qrcheckin.auth.AuthUser;

@RestController
@RequestMapping("/api/v1/courses")
public class AttendanceCsvController {

    private final AttendanceCsvService attendanceCsvService;

    public AttendanceCsvController(
            AttendanceCsvService attendanceCsvService) {
        this.attendanceCsvService = attendanceCsvService;
    }

    @GetMapping("/{courseId}/attendance/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable Long courseId,
            @AuthenticationPrincipal AuthUser admin) {

        byte[] csv = attendanceCsvService.export(courseId, admin);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"attendance_" + courseId + ".csv\"")
                .contentType(MediaType.parseMediaType(
                        "text/csv; charset=UTF-8"))
                .body(csv);
    }
}

