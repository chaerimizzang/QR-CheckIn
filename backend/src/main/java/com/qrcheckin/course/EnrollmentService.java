package com.qrcheckin.course;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.attendance.AttendanceRepository;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.CourseDtos.StudentResponse;
import com.qrcheckin.user.Role;
import com.qrcheckin.user.User;
import com.qrcheckin.user.UserRepository;

/** B-04 수강생 개별 등록·삭제 */
@Service
public class EnrollmentService {

    private final CourseService courseService;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    public EnrollmentService(CourseService courseService, EnrollmentRepository enrollmentRepository,
                             AttendanceRepository attendanceRepository, UserRepository userRepository) {
        this.courseService = courseService;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> list(Long courseId, AuthUser admin) {
        courseService.getOwnedCourse(courseId, admin);
        return enrollmentRepository.findByCourseId(courseId).stream()
                .map(e -> StudentResponse.from(e.getUser()))
                .sorted(Comparator.comparing(StudentResponse::name))
                .toList();
    }

    @Transactional
    public StudentResponse add(Long courseId, String email, AuthUser admin) {
        Course course = courseService.getOwnedCourse(courseId, admin);
        User student = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (student.getRole() != Role.STUDENT) {
            throw new ApiException(ErrorCode.NOT_A_STUDENT);
        }
        if (enrollmentRepository.existsByCourseIdAndUserId(courseId, student.getId())) {
            throw new ApiException(ErrorCode.ALREADY_ENROLLED);
        }
        try {
            enrollmentRepository.saveAndFlush(new Enrollment(course, student));
        } catch (DataIntegrityViolationException e) { // UNIQUE(course_id, user_id)
            throw new ApiException(ErrorCode.ALREADY_ENROLLED);
        }
        return StudentResponse.from(student);
    }

    /** 수강생 삭제 시 그 학생의 이 교육 출결 기록도 함께 지운다 */
    @Transactional
    public void remove(Long courseId, Long userId, AuthUser admin) {
        courseService.getOwnedCourse(courseId, admin);
        Enrollment enrollment = enrollmentRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "등록된 수강생이 아니에요"));
        attendanceRepository.deleteBySessionCourseIdAndUserId(courseId, userId);
        enrollmentRepository.delete(enrollment);
    }
}
