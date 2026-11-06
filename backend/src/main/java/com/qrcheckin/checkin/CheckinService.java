package com.qrcheckin.checkin;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.attendance.Attendance;
import com.qrcheckin.attendance.AttendanceRepository;
import com.qrcheckin.attendance.AttendanceStatus;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.checkin.CheckinDtos.CheckinRequest;
import com.qrcheckin.checkin.CheckinDtos.CheckinResponse;
import com.qrcheckin.checkin.GeoUtils.LocationVerdict;
import com.qrcheckin.checkin.QrTokenService.ParsedToken;
import com.qrcheckin.checkin.QrTokenService.Verdict;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.EnrollmentRepository;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.session.SessionStatus;
import com.qrcheckin.user.UserRepository;

/**
 * B-07 출석 인증 + B-09 오류 안내. 판정은 전부 서버 시각으로 한다(계획서 4장).
 *
 * 검사 순서: ① 토큰 형식·서명 → ② 수강 자격 → ③ 출석 시간 → ④ 토큰 만료 → ⑤ 위치 → ⑥ 중복
 * (위조 가능성이 큰 토큰을 먼저, 오차가 가장 큰 위치를 뒤에. 마감 후에는 '만료'보다 '마감'이 정확한 안내라 ③을 ④보다 앞에 둔다)
 */
@Service
public class CheckinService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** 계획서 4.4 재시도 방법 */
    private static final Map<ErrorCode, String> HINTS = Map.of(
            ErrorCode.QR_INVALID, "강의실 화면의 QR을 직접 스캔해 주세요",
            ErrorCode.QR_EXPIRED, "화면의 새 QR을 다시 스캔해 주세요",
            ErrorCode.NOT_ENROLLED, "관리자에게 수강생 등록을 요청해 주세요",
            ErrorCode.SESSION_NOT_OPEN, "안내된 출석 시간에 다시 시도해 주세요",
            ErrorCode.SESSION_CLOSED, "교수님(관리자)께 직접 말씀드려 지각 처리를 요청해 주세요",
            ErrorCode.LOW_ACCURACY, "휴대폰의 '정확한 위치'를 켜고 창가로 이동한 뒤 다시 시도해 주세요",
            ErrorCode.OUT_OF_RANGE, "강의실 안에서 다시 시도해 주세요");

    private final QrTokenService qrTokenService;
    private final ClassSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final int maxAccuracyM;

    public CheckinService(QrTokenService qrTokenService, ClassSessionRepository sessionRepository,
                          EnrollmentRepository enrollmentRepository, AttendanceRepository attendanceRepository,
                          UserRepository userRepository, Clock clock,
                          @Value("${app.checkin.max-accuracy-m}") int maxAccuracyM) {
        this.qrTokenService = qrTokenService;
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.maxAccuracyM = maxAccuracyM;
    }

    @Transactional
    public CheckinResponse checkIn(AuthUser student, CheckinRequest req) {
        LocalDateTime now = LocalDateTime.now(clock);

        // ① 토큰 형식·서명
        ParsedToken token = qrTokenService.parse(req.token().trim()).orElseThrow(() -> fail(ErrorCode.QR_INVALID));
        ClassSession session = sessionRepository.findById(token.sessionId())
                .orElseThrow(() -> fail(ErrorCode.QR_INVALID));
        if (session.getOpenedAt() == null || session.getQrSalt() == null) {
            throw fail(ErrorCode.QR_INVALID);
        }
        Verdict verdict = qrTokenService.verify(token, session.getQrSalt(),
                session.getOpenedAt().atZone(clock.getZone()).toInstant(), clock.instant());
        if (verdict == Verdict.INVALID) {
            throw fail(ErrorCode.QR_INVALID);
        }

        // ② 수강 자격
        Long courseId = session.getCourse().getId();
        if (!enrollmentRepository.existsByCourseIdAndUserId(courseId, student.id())) {
            throw fail(ErrorCode.NOT_ENROLLED);
        }

        // ③ 출석 시간
        if (session.getStatus() == SessionStatus.SCHEDULED || now.isBefore(session.getOpenedAt())) {
            throw fail(ErrorCode.SESSION_NOT_OPEN);
        }
        if (session.getStatus() == SessionStatus.CLOSED || !now.isBefore(session.getClosedAt())) {
            throw fail(ErrorCode.SESSION_CLOSED, ErrorCode.SESSION_CLOSED.defaultMessage(),
                    Map.of("closedAt", session.getClosedAt().format(HH_MM)));
        }

        // ④ 토큰 만료(현재·직전 주기만 허용)
        if (verdict == Verdict.EXPIRED) {
            throw fail(ErrorCode.QR_EXPIRED);
        }

        // ⑤ 위치: 정확도 → 거리
        double distance = GeoUtils.distanceMeters(req.latitude(), req.longitude(),
                session.getLatitude(), session.getLongitude());
        LocationVerdict location = GeoUtils.judge(distance, req.accuracy(), session.getRadiusM(), maxAccuracyM);
        if (location == LocationVerdict.LOW_ACCURACY) {
            long acc = Math.round(req.accuracy());
            throw fail(ErrorCode.LOW_ACCURACY, "위치 정확도가 낮아요(약 " + acc + "m)",
                    Map.of("accuracyM", acc, "maxAccuracyM", maxAccuracyM));
        }
        if (location == LocationVerdict.OUT_OF_RANGE) {
            long d = Math.round(distance);
            throw fail(ErrorCode.OUT_OF_RANGE, "출석 장소에서 약 " + d + "m 떨어져 있어요",
                    Map.of("distanceM", d, "radiusM", session.getRadiusM()));
        }

        // ⑥ 중복 — UNIQUE(session_id, user_id) 가 최종 방어선
        attendanceRepository.findBySessionIdAndUserId(session.getId(), student.id())
                .ifPresent(a -> { throw alreadyChecked(a); });
        Attendance saved;
        try {
            saved = attendanceRepository.saveAndFlush(
                    Attendance.byQr(session, userRepository.getReferenceById(student.id()), now));
        } catch (DataIntegrityViolationException e) { // 같은 학생이 동시에 두 번 보낸 경우
            throw new ApiException(ErrorCode.ALREADY_CHECKED);
        }
        return new CheckinResponse(saved.getStatus(), saved.getCheckedAt(), courseId,
                session.getCourse().getTitle(), session.getSessionNo());
    }

    private ApiException alreadyChecked(Attendance a) {
        String msg = "이미 출석 처리되었어요(" + a.getCheckedAt().format(HH_MM) + ", " + label(a.getStatus()) + ")";
        return new ApiException(ErrorCode.ALREADY_CHECKED, msg,
                Map.of("status", a.getStatus().name(), "checkedAt", a.getCheckedAt().toString()));
    }

    private static String label(AttendanceStatus status) {
        return switch (status) {
            case PRESENT -> "출석";
            case LATE -> "지각";
            case ABSENT -> "결석";
        };
    }

    private static ApiException fail(ErrorCode code) {
        return fail(code, code.defaultMessage(), Map.of());
    }

    private static ApiException fail(ErrorCode code, String message, Map<String, Object> extra) {
        Map<String, Object> detail = new LinkedHashMap<>(extra);
        String hint = HINTS.get(code);
        if (hint != null) {
            detail.put("hint", hint);
        }
        return new ApiException(code, message, detail);
    }
}
