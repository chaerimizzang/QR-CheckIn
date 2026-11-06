package com.qrcheckin.session;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.Course;
import com.qrcheckin.course.CourseService;
import com.qrcheckin.session.SessionDtos.SessionRequest;
import com.qrcheckin.session.SessionDtos.SessionResponse;

/** B-03 회차 등록, B-05 출석 조건 설정 */
@Service
public class SessionService {

    static final int DEFAULT_RADIUS_M = 50;

    private final ClassSessionRepository sessionRepository;
    private final CourseService courseService;
    private final int defaultCheckinMinutes;

    public SessionService(ClassSessionRepository sessionRepository, CourseService courseService,
                          @Value("${app.checkin.default-minutes}") int defaultCheckinMinutes) {
        this.sessionRepository = sessionRepository;
        this.courseService = courseService;
        this.defaultCheckinMinutes = defaultCheckinMinutes;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(Long courseId, AuthUser admin) {
        courseService.getOwnedCourse(courseId, admin);
        return sessionRepository.findByCourseIdOrderBySessionNoAsc(courseId).stream()
                .map(SessionResponse::from).toList();
    }

    @Transactional
    public SessionResponse create(Long courseId, SessionRequest req, AuthUser admin) {
        Course course = courseService.getOwnedCourse(courseId, admin);
        validateLocationPair(req);
        int sessionNo = sessionRepository.findMaxSessionNo(courseId) + 1;
        ClassSession session = new ClassSession(course, sessionNo, req.scheduledAt(),
                req.latitude(), req.longitude(), radiusOrDefault(req), minutesOrDefault(req));
        return SessionResponse.from(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public SessionResponse get(Long sessionId, AuthUser admin) {
        return SessionResponse.from(getOwnedSession(sessionId, admin));
    }

    /** 출석을 시작한 회차(OPEN/CLOSED)는 조건을 바꿀 수 없다 — 이미 기록된 출결의 근거가 바뀌기 때문 */
    @Transactional
    public SessionResponse update(Long sessionId, SessionRequest req, AuthUser admin) {
        ClassSession session = getOwnedSession(sessionId, admin);
        requireScheduled(session);
        validateLocationPair(req);
        session.updateConditions(req.scheduledAt(), req.latitude(), req.longitude(),
                radiusOrDefault(req), minutesOrDefault(req));
        return SessionResponse.from(session);
    }

    @Transactional
    public void delete(Long sessionId, AuthUser admin) {
        ClassSession session = getOwnedSession(sessionId, admin);
        requireScheduled(session);
        sessionRepository.delete(session);
    }

    /** 다른 서비스(QR·출결)에서도 쓰는 회차 소유권 검사 */
    @Transactional
    public ClassSession getOwnedSession(Long sessionId, AuthUser admin) {
        ClassSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없어요"));
        courseService.getOwnedCourse(session.getCourse().getId(), admin);
        return session;
    }

    private static void requireScheduled(ClassSession session) {
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new ApiException(ErrorCode.SESSION_NOT_EDITABLE);
        }
    }

    private static void validateLocationPair(SessionRequest req) {
        if ((req.latitude() == null) != (req.longitude() == null)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "위도와 경도는 함께 입력해야 해요");
        }
    }

    private static int radiusOrDefault(SessionRequest req) {
        return req.radiusM() != null ? req.radiusM() : DEFAULT_RADIUS_M;
    }

    private int minutesOrDefault(SessionRequest req) {
        return req.checkinMinutes() != null ? req.checkinMinutes() : defaultCheckinMinutes;
    }
}
