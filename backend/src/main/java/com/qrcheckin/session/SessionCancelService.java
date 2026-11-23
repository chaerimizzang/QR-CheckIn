package com.qrcheckin.session;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.EnrollmentRepository;
import com.qrcheckin.push.PushMessage;
import com.qrcheckin.push.PushService;
import com.qrcheckin.request.AttendanceRequest;
import com.qrcheckin.request.AttendanceRequestRepository;
import com.qrcheckin.request.RequestStatus;
import com.qrcheckin.session.SessionDtos.SessionResponse;

/**
 * 휴강(보강 없음).
 * - 출석을 시작하기 전(SCHEDULED) 회차만 휴강할 수 있다(날짜가 지났는데 열지 않은 회차도 가능)
 * - 휴강하면 그 회차의 처리 대기 출석 인정 신청은 취소되고, 앞으로의 수업이면 수강생에게 알린다
 * - [휴강 취소]하면 다시 예정 회차가 된다
 */
@Service
public class SessionCancelService {

    private static final Logger log = LoggerFactory.getLogger(SessionCancelService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN);

    private final SessionService sessionService;
    private final AttendanceRequestRepository requests;
    private final EnrollmentRepository enrollments;
    private final PushService pushService;
    private final Clock clock;

    public SessionCancelService(SessionService sessionService, AttendanceRequestRepository requests,
                                EnrollmentRepository enrollments, PushService pushService, Clock clock) {
        this.sessionService = sessionService;
        this.requests = requests;
        this.enrollments = enrollments;
        this.pushService = pushService;
        this.clock = clock;
    }

    @Transactional
    public SessionResponse cancel(Long sessionId, String reason, AuthUser admin) {
        ClassSession session = sessionService.getOwnedSession(sessionId, admin);
        session.getCourse().requireEditable();
        if (session.isCanceled()) {
            return SessionResponse.from(session); // 이미 휴강(두 번 눌러도 그대로)
        }
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new ApiException(ErrorCode.SESSION_NOT_EDITABLE, "출석을 시작한 회차는 휴강할 수 없어요");
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        session.cancel(why, now);

        // 휴강한 회차의 처리 대기 신청은 더 처리할 필요가 없다
        for (AttendanceRequest r : requests.findBySessionId(sessionId)) {
            if (r.getStatus() == RequestStatus.PENDING) {
                r.cancel(now);
            }
        }

        if (session.getScheduledAt().isAfter(now)) {
            notifyStudents(session, why);
        }
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse uncancel(Long sessionId, AuthUser admin) {
        ClassSession session = sessionService.getOwnedSession(sessionId, admin);
        session.getCourse().requireEditable();
        if (!session.isCanceled()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "휴강한 회차가 아니에요");
        }
        session.uncancel();
        return SessionResponse.from(session);
    }

    /** "자료구조 11/25(화) 13:00 수업은 휴강이에요 · 사유" — 알림이 실패해도 휴강은 그대로 처리 */
    private void notifyStudents(ClassSession s, String reason) {
        try {
            List<Long> to = enrollments.findByCourseId(s.getCourse().getId()).stream()
                    .map(e -> e.getUser().getId()).toList();
            if (to.isEmpty()) {
                return;
            }
            String body = s.getScheduledAt().format(DAY) + " 수업은 휴강이에요" + (reason == null ? "" : " · " + reason);
            pushService.send(to, new PushMessage("휴강 [" + s.getCourse().getTitle() + "]", body,
                    "/student/courses/" + s.getCourse().getId(), "cancel-" + s.getId()));
        } catch (RuntimeException e) {
            log.warn("휴강 알림 실패: {}", e.toString());
        }
    }
}
