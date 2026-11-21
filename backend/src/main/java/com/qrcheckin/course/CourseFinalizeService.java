package com.qrcheckin.course;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.DependentDataCleaner;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.CourseDtos.CourseResponse;
import com.qrcheckin.course.CourseDtos.FinalizeCheck;
import com.qrcheckin.course.CourseDtos.SemesterResponse;
import com.qrcheckin.request.AttendanceRequestRepository;
import com.qrcheckin.request.RequestStatus;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.session.SessionStatus;

/**
 * 학기와 출결 확정.
 * 확정 조건: 진행 중(OPEN) 회차가 없고, 처리 안 한 출석 인정 신청이 없을 것.
 * 확정하면 한 번도 열지 않은 예정 회차는 지운다(수업하지 않은 회차가 F 기준 계산에 남지 않도록).
 * 확정된 교육은 읽기 전용이고, 교수가 [확정 취소]로 되돌릴 수 있다.
 */
@Service
public class CourseFinalizeService {

    private final CourseService courseService;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final ClassSessionRepository sessions;
    private final AttendanceRequestRepository requests;
    private final DependentDataCleaner cleaner;
    private final Clock clock;

    public CourseFinalizeService(CourseService courseService, CourseRepository courses,
                                 EnrollmentRepository enrollments, ClassSessionRepository sessions,
                                 AttendanceRequestRepository requests, DependentDataCleaner cleaner, Clock clock) {
        this.courseService = courseService;
        this.courses = courses;
        this.enrollments = enrollments;
        this.sessions = sessions;
        this.requests = requests;
        this.cleaner = cleaner;
        this.clock = clock;
    }

    /** 오늘 기준 현재 학기 */
    public SemesterResponse current() {
        return SemesterResponse.of(Semester.of(LocalDate.now(clock)));
    }

    /** 학기 선택 목록: 현재 학기 + 내 교육들이 있는 학기(최신순) */
    @Transactional(readOnly = true)
    public List<SemesterResponse> mySemesters(AuthUser user) {
        List<Course> mine = user.isAdmin()
                ? courses.findByOwnerIdOrderByIdDesc(user.id())
                : enrollments.findByUserId(user.id()).stream().map(Enrollment::getCourse).toList();
        TreeSet<Semester> set = new TreeSet<>(Semester.NEWEST_FIRST);
        set.add(Semester.of(LocalDate.now(clock)));
        mine.stream().map(Course::getSemester).filter(java.util.Objects::nonNull).forEach(set::add);
        return set.stream().map(SemesterResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public FinalizeCheck check(Long courseId, AuthUser admin) {
        courseService.getOwnedCourse(courseId, admin);
        return checkOf(courseId);
    }

    @Transactional
    public CourseResponse finalizeCourse(Long courseId, AuthUser admin) {
        Course course = courseService.getOwnedCourse(courseId, admin);
        course.requireEditable();
        FinalizeCheck c = checkOf(courseId);
        if (c.openSessions() > 0) {
            throw new ApiException(ErrorCode.FINALIZE_BLOCKED, "출석이 진행 중인 회차가 있어요. 먼저 마감해 주세요",
                    Map.<String, Object>of("openSessions", c.openSessions()));
        }
        if (c.pendingRequests() > 0) {
            throw new ApiException(ErrorCode.FINALIZE_BLOCKED,
                    "처리하지 않은 출석 인정 신청이 " + c.pendingRequests() + "건 있어요. 먼저 승인·반려해 주세요",
                    Map.<String, Object>of("pendingRequests", c.pendingRequests()));
        }
        for (ClassSession s : sessions.findByCourseIdOrderBySessionNoAsc(courseId)) {
            if (s.getStatus() == SessionStatus.SCHEDULED) {
                cleaner.forSession(s.getId());
                sessions.delete(s);
            }
        }
        course.finalizeAttendance(LocalDateTime.now(clock));
        return courseService.get(courseId, admin);
    }

    @Transactional
    public CourseResponse reopen(Long courseId, AuthUser admin) {
        Course course = courseService.getOwnedCourse(courseId, admin);
        course.reopen();
        return courseService.get(courseId, admin);
    }

    private FinalizeCheck checkOf(Long courseId) {
        List<ClassSession> all = sessions.findByCourseIdOrderBySessionNoAsc(courseId);
        long open = all.stream().filter(s -> s.getStatus() == SessionStatus.OPEN).count();
        long unopened = all.stream().filter(s -> s.getStatus() == SessionStatus.SCHEDULED).count();
        long pending = requests.countBySessionCourseIdAndStatus(courseId, RequestStatus.PENDING);
        return new FinalizeCheck(open, pending, unopened);
    }
}
