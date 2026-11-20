package com.qrcheckin.profile;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.attendance.AttendanceRateResponse.StudentRate;
import com.qrcheckin.attendance.AttendanceRateService;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.Course;
import com.qrcheckin.course.CourseRepository;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.course.EnrollmentRepository;
import com.qrcheckin.profile.StudentProfileResponse.CourseSummary;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.session.SessionStatus;
import com.qrcheckin.user.User;
import com.qrcheckin.user.UserRepository;

/** 프로필(학생·관리자) — 학생 출석률 계산은 D-17(AttendanceRateService)과 같은 규칙을 그대로 쓴다 */
@Service
public class StudentProfileService {

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassSessionRepository sessionRepository;
    private final AttendanceRateService rateService;
    private final CourseRepository courseRepository;

    public StudentProfileService(UserRepository userRepository, EnrollmentRepository enrollmentRepository,
                                 ClassSessionRepository sessionRepository, AttendanceRateService rateService,
                                 CourseRepository courseRepository) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionRepository = sessionRepository;
        this.rateService = rateService;
        this.courseRepository = courseRepository;
    }

    /** 관리자 프로필: 내가 개설한 교육별 수강생·회차 수, 전체 수강생 수(중복 제외) */
    @Transactional(readOnly = true)
    public AdminProfileResponse adminProfile(AuthUser admin) {
        User me = userRepository.findById(admin.id())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        List<Course> owned = courseRepository.findByOwnerIdOrderByIdDesc(me.getId());

        List<AdminProfileResponse.CourseSummary> courses = owned.stream().map(c -> {
            List<ClassSession> sessions = sessionRepository.findByCourseIdOrderBySessionNoAsc(c.getId());
            int closed = (int) sessions.stream().filter(s -> s.getStatus() == SessionStatus.CLOSED).count();
            return new AdminProfileResponse.CourseSummary(
                    c.getId(), c.getTitle(), enrollmentRepository.countByCourseId(c.getId()), sessions.size(), closed);
        }).toList();

        long distinctStudents = owned.stream()
                .flatMap(c -> enrollmentRepository.findByCourseId(c.getId()).stream())
                .map(e -> e.getUser().getId())
                .distinct()
                .count();

        return new AdminProfileResponse(me.getName(), me.getEmail(), owned.size(), distinctStudents, courses);
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse myProfile(AuthUser student) {
        User me = userRepository.findById(student.id())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        // 본인이 수강 중인 교육만(수강 등록 기준) — 남의 출결은 볼 수 없다
        List<CourseSummary> courses = enrollmentRepository.findByUserId(me.getId()).stream()
                .map(e -> summary(e.getCourse(), me))
                .toList();

        return new StudentProfileResponse(me.getName(), me.getStudentNo(), me.getEmail(), courses);
    }

    private CourseSummary summary(Course course, User me) {
        StudentRate r = rateService.rateOfStudent(course.getId(), me);
        int total = (int) sessionRepository.findByCourseIdOrderBySessionNoAsc(course.getId()).stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED)
                .count();
        return new CourseSummary(
                course.getId(),
                course.getTitle(),
                course.getOwner().getName(),
                total,
                r.presentCount(),
                r.lateCount(),
                r.absentCount(),
                r.convertedAbsences(),
                total == 0 ? null : r.attendanceRate());
    }
}
