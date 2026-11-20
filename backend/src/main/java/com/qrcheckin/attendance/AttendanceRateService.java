package com.qrcheckin.attendance;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.attendance.AttendanceRateResponse.StudentRate;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.course.CourseService;
import com.qrcheckin.course.Enrollment;
import com.qrcheckin.course.EnrollmentRepository;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.session.SessionStatus;
import com.qrcheckin.user.User;

@Service
public class AttendanceRateService {

    private final CourseService courseService;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassSessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;

    public AttendanceRateService(
            CourseService courseService,
            EnrollmentRepository enrollmentRepository,
            ClassSessionRepository sessionRepository,
            AttendanceRepository attendanceRepository) {

        this.courseService = courseService;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional(readOnly = true)
    public AttendanceRateResponse calculate(Long courseId, AuthUser admin) {

        // 해당 교육의 관리자만 조회 가능
        courseService.getOwnedCourse(courseId, admin);

        // 마감된 회차만 출석률 계산에 포함
        List<ClassSession> closedSessions = sessionRepository
                .findByCourseIdOrderBySessionNoAsc(courseId)
                .stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED)
                .toList();

        int totalSessions = closedSessions.size();

        // 교육에 등록된 학생 전체 조회
        List<Enrollment> enrollments =
                enrollmentRepository.findByCourseId(courseId);

        List<StudentRate> students = enrollments.stream()
                .map(enrollment -> rateOf(enrollment.getUser(), courseId, closedSessions))
                .toList();

        return new AttendanceRateResponse(
                courseId,
                totalSessions,
                students
        );
    }

    /** 학생 프로필용: 수강생 본인의 이 교육 출석률(교수 권한 검사 없음 — 호출하는 쪽에서 수강 여부 확인) */
    @Transactional(readOnly = true)
    public StudentRate rateOfStudent(Long courseId, User student) {
        return rateOf(student, courseId, closedSessions(courseId));
    }

    /** 마감된 회차만 출석률 계산에 포함 */
    private List<ClassSession> closedSessions(Long courseId) {
        return sessionRepository
                .findByCourseIdOrderBySessionNoAsc(courseId)
                .stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED)
                .toList();
    }

    /** 학생 한 명의 출석률 — 지각 3회 = 결석 1회, 마감 회차에 기록이 없으면 결석 */
    private StudentRate rateOf(User user, Long courseId, List<ClassSession> closedSessions) {
        int totalSessions = closedSessions.size();

        // 학생의 출결 기록을 회차별로 조회
        Map<Long, Attendance> attendanceMap =
                attendanceRepository
                        .findByUserIdAndSessionCourseIdOrderBySessionSessionNoAsc(
                                user.getId(), courseId)
                        .stream()
                        .collect(Collectors.toMap(
                                a -> a.getSession().getId(),
                                Function.identity()
                        ));

        long present = 0;
        long late = 0;
        long absent = 0;

        for (ClassSession session : closedSessions) {

            Attendance attendance =
                    attendanceMap.get(session.getId());

            // 마감된 회차에 기록이 없다면 결석으로 계산
            if (attendance == null) {
                absent++;
                continue;
            }

            switch (attendance.getStatus()) {
                case PRESENT -> present++;
                case LATE -> late++;
                case ABSENT -> absent++;
            }
        }

        // 지각 3회 = 결석 1회
        long convertedAbsences = absent + (late / 3);

        // 출석률 계산
        double attendanceRate = totalSessions == 0
                ? 0.0
                : Math.round(
                        (totalSessions - convertedAbsences)
                                * 10000.0 / totalSessions
                  ) / 100.0;

        return new StudentRate(
                user.getId(),
                user.getStudentNo(),
                user.getName(),
                present,
                late,
                absent,
                convertedAbsences,
                attendanceRate
        );
    }
}
