package com.qrcheckin.course;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.attendance.AttendanceRepository;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.DependentDataCleaner;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.CourseDtos.CourseRequest;
import com.qrcheckin.course.CourseDtos.CourseResponse;
import com.qrcheckin.schedule.CourseScheduleRepository;
import com.qrcheckin.schedule.ScheduleDtos.ScheduleSlot;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.user.UserRepository;

/** B-02 교육 관리. 관리자라도 본인이 개설한 교육만 다룰 수 있다(계획서 9.2). */
@Service
public class CourseService {

    private final DependentDataCleaner cleaner;
    private final java.time.Clock clock;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassSessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final CourseScheduleRepository scheduleRepository;

    public CourseService(CourseRepository courseRepository, EnrollmentRepository enrollmentRepository,
                         ClassSessionRepository sessionRepository, AttendanceRepository attendanceRepository,
                         UserRepository userRepository, CourseScheduleRepository scheduleRepository,
                         DependentDataCleaner cleaner, java.time.Clock clock) {
        this.cleaner = cleaner;
        this.clock = clock;
        this.scheduleRepository = scheduleRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
    }

    /** 관리자는 개설한 교육, 수강생은 등록된 교육 */
    @Transactional(readOnly = true)
    public List<CourseResponse> myCourses(AuthUser user) {
        List<Course> courses = user.isAdmin()
                ? courseRepository.findByOwnerIdOrderByIdDesc(user.id())
                : enrollmentRepository.findByUserId(user.id()).stream().map(Enrollment::getCourse).toList();
        return courses.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse get(Long courseId, AuthUser user) {
        Course course = findCourse(courseId);
        boolean allowed = user.isAdmin()
                ? course.isOwnedBy(user.id())
                : enrollmentRepository.existsByCourseIdAndUserId(courseId, user.id());
        if (!allowed) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return toResponse(course);
    }

    @Transactional
    public CourseResponse create(CourseRequest req, AuthUser user) {
        validateDates(req);
        Course course = new Course(userRepository.getReferenceById(user.id()), req.title().trim(),
                req.startDate(), req.endDate());
        course.setSemester(semesterOf(req, null));
        return toResponse(courseRepository.save(course));
    }

    @Transactional
    public CourseResponse update(Long courseId, CourseRequest req, AuthUser user) {
        validateDates(req);
        Course course = getOwnedCourse(courseId, user);
        course.requireEditable();
        course.update(req.title().trim(), req.startDate(), req.endDate());
        course.setSemester(semesterOf(req, course.getSemester()));
        return toResponse(course);
    }

    /** 교육 삭제 — 출결 → 회차 → 수강 등록 → 시간표 → 교육 순서로 지운다 */
    @Transactional
    public void delete(Long courseId, AuthUser user) {
        Course course = getOwnedCourse(courseId, user);
        course.requireEditable(); // 출결이 확정된 교육은 기록 보존을 위해 지울 수 없다
        cleaner.forCourse(courseId); // 수정 이력·출석 인정 신청
        attendanceRepository.deleteBySessionCourseId(courseId);
        sessionRepository.deleteByCourseId(courseId);
        enrollmentRepository.deleteByCourseId(courseId);
        scheduleRepository.deleteByCourseId(courseId);
        courseRepository.delete(course);
    }

    /** 다른 서비스에서도 쓰는 소유권 검사: 없으면 404, 남의 교육이면 403 */
    @Transactional
    public Course getOwnedCourse(Long courseId, AuthUser user) {
        Course course = findCourse(courseId);
        if (!user.isAdmin() || !course.isOwnedBy(user.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return course;
    }

    /** 학기: 요청에 있으면 그것, 없으면 기존 값, 그것도 없으면 시작일(없으면 오늘) 기준 */
    private Semester semesterOf(CourseRequest req, Semester current) {
        if (req.semesterYear() != null && req.semesterTerm() != null) {
            if (req.semesterYear() < 2000 || req.semesterYear() > 2100) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "학기 연도를 확인해 주세요");
            }
            return new Semester(req.semesterYear(), req.semesterTerm());
        }
        if (current != null) {
            return current;
        }
        return Semester.of(req.startDate() != null ? req.startDate() : java.time.LocalDate.now(clock));
    }

    private Course findCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "교육을 찾을 수 없어요"));
    }

    private CourseResponse toResponse(Course c) {
        List<ScheduleSlot> schedules = scheduleRepository.findByCourseId(c.getId()).stream()
                .map(ScheduleSlot::from).sorted(ScheduleSlot.ORDER).toList();
        return CourseResponse.of(c, enrollmentRepository.countByCourseId(c.getId()), schedules);
    }

    private static void validateDates(CourseRequest req) {
        if (req.startDate() != null && req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "종료일이 시작일보다 빨라요");
        }
    }
}
