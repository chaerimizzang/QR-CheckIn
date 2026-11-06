package com.qrcheckin.course;

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
import com.qrcheckin.course.CourseDtos.CourseRequest;
import com.qrcheckin.course.CourseDtos.CourseResponse;
import com.qrcheckin.course.CourseDtos.EnrollmentRequest;
import com.qrcheckin.course.CourseDtos.StudentResponse;

import jakarta.validation.Valid;

/** B-02 교육, B-04 수강생 */
@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseService courseService;
    private final EnrollmentService enrollmentService;

    public CourseController(CourseService courseService, EnrollmentService enrollmentService) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public List<CourseResponse> myCourses(@AuthenticationPrincipal AuthUser user) {
        return courseService.myCourses(user);
    }

    @GetMapping("/{courseId}")
    public CourseResponse get(@PathVariable Long courseId, @AuthenticationPrincipal AuthUser user) {
        return courseService.get(courseId, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CourseResponse create(@Valid @RequestBody CourseRequest req, @AuthenticationPrincipal AuthUser user) {
        return courseService.create(req, user);
    }

    @PutMapping("/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public CourseResponse update(@PathVariable Long courseId, @Valid @RequestBody CourseRequest req,
                                 @AuthenticationPrincipal AuthUser user) {
        return courseService.update(courseId, req, user);
    }

    @DeleteMapping("/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long courseId, @AuthenticationPrincipal AuthUser user) {
        courseService.delete(courseId, user);
    }

    @GetMapping("/{courseId}/enrollments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<StudentResponse> students(@PathVariable Long courseId, @AuthenticationPrincipal AuthUser user) {
        return enrollmentService.list(courseId, user);
    }

    @PostMapping("/{courseId}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public StudentResponse addStudent(@PathVariable Long courseId, @Valid @RequestBody EnrollmentRequest req,
                                      @AuthenticationPrincipal AuthUser user) {
        return enrollmentService.add(courseId, req.email(), user);
    }

    @DeleteMapping("/{courseId}/enrollments/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void removeStudent(@PathVariable Long courseId, @PathVariable Long userId,
                              @AuthenticationPrincipal AuthUser user) {
        enrollmentService.remove(courseId, userId, user);
    }
}
