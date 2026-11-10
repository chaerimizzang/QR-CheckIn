package com.qrcheckin.attendance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.attendance.AttendanceDtos.MyAttendanceResponse;
import com.qrcheckin.attendance.AttendanceDtos.MyRow;
import com.qrcheckin.attendance.AttendanceDtos.RosterResponse;
import com.qrcheckin.attendance.AttendanceDtos.AttendanceHistoryRow;
import com.qrcheckin.attendance.AttendanceDtos.RosterRow;
import com.qrcheckin.attendance.AttendanceDtos.Summary;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.Course;
import com.qrcheckin.course.CourseRepository;
import com.qrcheckin.course.Enrollment;
import com.qrcheckin.course.EnrollmentRepository;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.session.SessionService;
import com.qrcheckin.user.User;
import com.qrcheckin.user.UserRepository;

/** B-10 본인 출결, B-11 회차 현황, B-12 관리자 출결 수정 */
@Service
public class AttendanceService {

    private final SessionService sessionService;
    private final ClassSessionRepository sessionRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceHistoryRepository attendanceHistoryRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AttendanceService(SessionService sessionService, ClassSessionRepository sessionRepository,
                             CourseRepository courseRepository, EnrollmentRepository enrollmentRepository,
                             AttendanceRepository attendanceRepository, AttendanceHistoryRepository attendanceHistoryRepository,
                             UserRepository userRepository, Clock clock) {
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.attendanceHistoryRepository = attendanceHistoryRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** B-11: 수강생 전원을 이름순으로, 출결 기록이 있으면 함께 */
    @Transactional(readOnly = true)
    public RosterResponse roster(Long sessionId, AuthUser admin) {
        ClassSession session = sessionService.getOwnedSession(sessionId, admin);
        Map<Long, Attendance> byUser = attendanceRepository.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(a -> a.getUser().getId(), Function.identity()));

        List<RosterRow> rows = enrollmentRepository.findByCourseId(session.getCourse().getId()).stream()
                .map(Enrollment::getUser)
                .sorted(Comparator.comparing(User::getName))
                .map(u -> {
                    Attendance a = byUser.get(u.getId());
                    return new RosterRow(u.getId(), u.getName(), u.getStudentNo(), u.getEmail(),
                            a == null ? null : a.getId(),
                            a == null ? null : a.getStatus(),
                            a == null ? null : a.getCheckedAt(),
                            a == null ? null : a.getSource());
                })
                .toList();

        long present = rows.stream().filter(r -> r.status() == AttendanceStatus.PRESENT).count();
        long late = rows.stream().filter(r -> r.status() == AttendanceStatus.LATE).count();
        long absent = rows.stream().filter(r -> r.status() == AttendanceStatus.ABSENT).count();
        Summary summary = new Summary(rows.size(), present, late, absent, rows.size() - present - late - absent);

        return new RosterResponse(session.getId(), session.getSessionNo(), session.getStatus(),
                session.getOpenedAt(), session.getClosedAt(), summary, rows);
    }

    /**
     * B-12: 회차 상태·위치와 관계없이 언제든 변경(계획서 3.3). 기록이 없으면 새로 만든다.
     * source = MANUAL 이 되므로 마감 스케줄러가 다시 덮어쓰지 않는다.
     */
    @Transactional
    public RosterRow changeStatus(Long sessionId, Long userId, AttendanceStatus status, AuthUser admin) {
        ClassSession session = sessionService.getOwnedSession(sessionId, admin);
        Enrollment enrollment = enrollmentRepository.findByCourseIdAndUserId(session.getCourse().getId(), userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "이 교육의 수강생이 아니에요"));
        LocalDateTime now = LocalDateTime.now(clock);
        
        //Find the existing attendance record
        Attendance attendance = attendanceRepository.findBySessionIdAndUserId(sessionId, userId)
            .orElse(null);
        AttendanceStatus oldStatus;
        boolean statusChanged;

        if (attendance == null) {
        oldStatus = null; //No previous record->create a new one
        attendance = attendanceRepository.save(
                Attendance.manual(session, enrollment.getUser(), status, now));
        statusChanged = true;

        } else {
        oldStatus = attendance.getStatus(); //Remember the previous status
        statusChanged = oldStatus != status; //Update only if the status is different

        if (statusChanged) {
            attendance.changeByAdmin(status, now);}
        }

        if (statusChanged) { //Saving the modification history
                User adminUser = userRepository.findById(admin.id()).orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND, "관리자를 찾을 수 없습니다"));

        AttendanceHistory history = new AttendanceHistory(attendance, adminUser, oldStatus, status, now);
        attendanceHistoryRepository.save(history);
        }
        User u = enrollment.getUser();  //Returningthe updated attendance information

        return new RosterRow(u.getId(), u.getName(), u.getStudentNo(), u.getEmail(), attendance.getId(),
            attendance.getStatus(), attendance.getCheckedAt(), attendance.getSource());
        }

        @Transactional(readOnly = true)
        public List<AttendanceHistoryRow> getAttendanceHistory(Long sessionId, Long userId, AuthUser admin) {
        ClassSession session = sessionService.getOwnedSession(sessionId, admin); //Checking administrator's access to the session
        enrollmentRepository.findByCourseIdAndUserId(// Check student enrollment
            session.getCourse().getId(), userId ).orElseThrow(() -> new ApiException(
            ErrorCode.NOT_FOUND, "이 교육의 수강생이 아니에요"));
        
        Attendance attendance = attendanceRepository .findBySessionIdAndUserId(sessionId, userId)
            .orElse(null); //Finding attendance record

        if (attendance == null) {
                return List.of();
        }

        //Returning history, newest first
         return attendanceHistoryRepository.findByAttendanceIdOrderByModifiedAtDesc(attendance.getId())
            .stream().map(history -> new AttendanceHistoryRow(history.getId(),history.getOldStatus(),history.getNewStatus(),
                    history.getModifiedBy().getName(),history.getModifiedAt()))
            .toList();
        }


    /** B-10: 교육의 모든 회차를 회차 순서대로, 본인 출결과 함께 */
    @Transactional(readOnly = true)
    public MyAttendanceResponse myAttendances(Long courseId, AuthUser student) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "교육을 찾을 수 없어요"));
        if (!enrollmentRepository.existsByCourseIdAndUserId(courseId, student.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "수강 중인 교육이 아니에요");
        }
        Map<Long, Attendance> bySession = attendanceRepository
                .findByUserIdAndSessionCourseIdOrderBySessionSessionNoAsc(student.id(), courseId).stream()
                .collect(Collectors.toMap(a -> a.getSession().getId(), Function.identity()));

        List<MyRow> rows = sessionRepository.findByCourseIdOrderBySessionNoAsc(courseId).stream()
                .map(s -> {
                    Attendance a = bySession.get(s.getId());
                    return new MyRow(s.getId(), s.getSessionNo(), s.getScheduledAt(), s.getStatus(),
                            a == null ? null : a.getStatus(), a == null ? null : a.getCheckedAt());
                })
                .toList();
        return new MyAttendanceResponse(course.getId(), course.getTitle(), rows);
    }
}
