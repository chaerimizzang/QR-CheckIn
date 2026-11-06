package com.qrcheckin.checkin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.attendance.Attendance;
import com.qrcheckin.attendance.AttendanceRepository;
import com.qrcheckin.attendance.AttendanceStatus;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.checkin.CheckinDtos.QrResponse;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.Enrollment;
import com.qrcheckin.course.EnrollmentRepository;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.session.SessionDtos.SessionResponse;
import com.qrcheckin.session.SessionService;
import com.qrcheckin.session.SessionStatus;

/** B-06 출석 시작·종료·QR 발급, B-08 마감 시 자동 결석 */
@Service
public class SessionRunService {

    private static final Logger log = LoggerFactory.getLogger(SessionRunService.class);

    private final SessionService sessionService;
    private final ClassSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final QrTokenService qrTokenService;
    private final Clock clock;
    private final String frontendUrl;
    private final int refreshSeconds;

    public SessionRunService(SessionService sessionService, ClassSessionRepository sessionRepository,
                             EnrollmentRepository enrollmentRepository, AttendanceRepository attendanceRepository,
                             QrTokenService qrTokenService, Clock clock,
                             @Value("${app.frontend-url}") String frontendUrl,
                             @Value("${app.qr.refresh-seconds}") int refreshSeconds) {
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.qrTokenService = qrTokenService;
        this.clock = clock;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.refreshSeconds = refreshSeconds;
    }

    /** [출석 시작]: SCHEDULED → OPEN, 마감 시각 = 지금 + QR 출석 시간 */
    @Transactional
    public SessionResponse open(Long sessionId, AuthUser admin) {
        ClassSession session = sessionService.getOwnedSession(sessionId, admin);
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new ApiException(ErrorCode.SESSION_ALREADY_STARTED);
        }
        if (!session.hasLocation()) {
            throw new ApiException(ErrorCode.LOCATION_REQUIRED);
        }
        // MySQL DATETIME 은 초 단위로 저장되므로, 저장 전후 값이 같도록 초 단위로 자른다
        session.open(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS), QrTokenService.newSalt());
        return SessionResponse.from(session);
    }

    /** [출석 종료]: 마감 시각을 지금으로 앞당기고 미출석자를 결석 처리 */
    @Transactional
    public SessionResponse close(Long sessionId, AuthUser admin) {
        ClassSession session = sessionService.getOwnedSession(sessionId, admin);
        if (session.getStatus() == SessionStatus.SCHEDULED) {
            throw new ApiException(ErrorCode.SESSION_NOT_OPEN, "아직 출석을 시작하지 않은 회차예요");
        }
        if (session.getStatus() == SessionStatus.OPEN) {
            finalizeSession(session, LocalDateTime.now(clock));
        }
        return SessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public QrResponse currentQr(Long sessionId, AuthUser admin) {
        ClassSession session = sessionService.getOwnedSession(sessionId, admin);
        LocalDateTime now = LocalDateTime.now(clock);
        if (session.getStatus() == SessionStatus.SCHEDULED) {
            throw new ApiException(ErrorCode.SESSION_NOT_OPEN, "아직 출석을 시작하지 않은 회차예요");
        }
        if (session.getStatus() == SessionStatus.CLOSED || !now.isBefore(session.getClosedAt())) {
            throw new ApiException(ErrorCode.SESSION_CLOSED);
        }
        Instant opened = toInstant(session.getOpenedAt());
        Instant nowInstant = clock.instant();
        String token = qrTokenService.issue(session.getId(), session.getQrSalt(), opened, nowInstant);
        long remaining = Duration.between(now, session.getClosedAt()).toSeconds();
        return new QrResponse(
                token,
                frontendUrl + "/check-in?t=" + token,
                refreshSeconds,
                qrTokenService.millisUntilNextWindow(opened, nowInstant),
                session.getClosedAt(),
                remaining,
                attendanceRepository.countBySessionIdAndStatus(session.getId(), AttendanceStatus.PRESENT),
                enrollmentRepository.countByCourseId(session.getCourse().getId()));
    }

    /** B-08: 스케줄러가 1분마다 호출. 마감 시각이 지난 OPEN 회차를 모두 마감한다 */
    @Transactional
    public int closeExpiredSessions() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<ClassSession> expired = sessionRepository.findByStatusAndClosedAtLessThanEqual(SessionStatus.OPEN, now);
        for (ClassSession session : expired) {
            finalizeSession(session, now);
        }
        if (!expired.isEmpty()) {
            log.info("자동 마감: {}개 회차", expired.size());
        }
        return expired.size();
    }

    /**
     * 회차를 CLOSED 로 바꾸고, 출결 기록이 없는 수강생만 ABSENT(SYSTEM)로 넣는다.
     * 이미 기록이 있는 학생(QR 출석, 관리자 수정)은 건드리지 않으므로 여러 번 실행해도 결과가 같다(멱등).
     */
    void finalizeSession(ClassSession session, LocalDateTime now) {
        session.close(now);
        for (Enrollment e : enrollmentRepository.findByCourseId(session.getCourse().getId())) {
            if (attendanceRepository.findBySessionIdAndUserId(session.getId(), e.getUser().getId()).isEmpty()) {
                attendanceRepository.save(Attendance.absentBySystem(session, e.getUser(), now));
            }
        }
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt.atZone(clock.getZone()).toInstant();
    }
}
