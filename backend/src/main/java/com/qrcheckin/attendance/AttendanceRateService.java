package com.qrcheckin.attendance;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
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
    private final double failRatio;

    public AttendanceRateService(
            CourseService courseService,
            EnrollmentRepository enrollmentRepository,
            ClassSessionRepository sessionRepository,
            AttendanceRepository attendanceRepository,
            @Value("${app.attendance.fail-ratio:0.25}") double failRatio) {

        this.failRatio = failRatio;

        this.courseService = courseService;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional(readOnly = true)
    public AttendanceRateResponse calculate(Long courseId, AuthUser admin) {

        // 해당 교육의 관리자만 조회 가능
        courseService.getOwnedCourse(courseId, admin);

        // 마감된 회차만 출석률 계산에 포함(전체 회차 수는 F 기준 계산용)
        List<ClassSession> allSessions = sessionRepository.findByCourseIdOrderBySessionNoAsc(courseId);
        int planned = allSessions.size();
        List<ClassSession> closedSessions = allSessions
                .stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED)
                .toList();

        int totalSessions = closedSessions.size();

        // 교육에 등록된 학생 전체 조회
        List<Enrollment> enrollments =
                enrollmentRepository.findByCourseId(courseId);

        List<StudentRate> students = enrollments.stream()
                .map(enrollment -> rateOf(enrollment.getUser(), courseId, closedSessions, planned))
                .toList();

        return new AttendanceRateResponse(
                courseId,
                totalSessions,
                planned,
                AbsenceRule.of(planned, 0, failRatio).allowedAbsences(),
                students
        );
    }

    /** 결석 경고 알림용: 교육의 모든 수강생 출석률(권한 검사 없음 — 서버 내부에서만 호출) */
    @Transactional(readOnly = true)
    public List<StudentRate> ratesOfCourse(Long courseId) {
        List<ClassSession> all = sessionRepository.findByCourseIdOrderBySessionNoAsc(courseId);
        List<ClassSession> closed = all.stream().filter(s -> s.getStatus() == SessionStatus.CLOSED).toList();
        return enrollmentRepository.findByCourseId(courseId).stream()
                .map(e -> rateOf(e.getUser(), courseId, closed, all.size()))
                .toList();
    }

    /** 학생 프로필용: 수강생 본인의 이 교육 출석률(교수 권한 검사 없음 — 호출하는 쪽에서 수강 여부 확인) */
    @Transactional(readOnly = true)
    public StudentRate rateOfStudent(Long courseId, User student) {
        return rateOf(student, courseId, closedSessions(courseId),
                sessionRepository.findByCourseIdOrderBySessionNoAsc(courseId).size());
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
    private StudentRate rateOf(User user, Long courseId, List<ClassSession> closedSessions, int plannedSessions) {
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
        long excused = 0;

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
                case EXCUSED -> excused++;
            }
        }

        // 지각 3회 = 결석 1회
        long convertedAbsences = absent + (late / 3);

        // F 기준(결석이 전체 수업의 1/4 초과) 경고
        AbsenceRule.Status warning = AbsenceRule.of(plannedSessions, convertedAbsences, failRatio);

        // 출석률 계산 — 공결(출석 인정) 회차는 분모에서 뺀다
        long counted = totalSessions - excused;
        double attendanceRate = totalSessions == 0
                ? 0.0
                : counted == 0
                ? 100.0
                : Math.round(
                        (counted - convertedAbsences)
                                * 10000.0 / counted
                  ) / 100.0;

        return new StudentRate(
                user.getId(),
                user.getStudentNo(),
                user.getName(),
                present,
                late,
                absent,
                excused,
                convertedAbsences,
                attendanceRate,
                warning.remaining(),
                warning.level()
        );
    }
}
