
package com.qrcheckin.course;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.qrcheckin.auth.AuthUser;

@RestController
@RequestMapping("/api/v1/courses")
public class StudentCsvController {

    private final StudentCsvService studentCsvService;

    public StudentCsvController(StudentCsvService studentCsvService) {
        this.studentCsvService = studentCsvService;
    }

    @PostMapping(
        value = "/{courseId}/enrollments/csv",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasRole('ADMIN')")
    public StudentCsvResult uploadCsv(
            @PathVariable Long courseId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthUser admin) {

        return studentCsvService.upload(courseId, file, admin);
    }
}
