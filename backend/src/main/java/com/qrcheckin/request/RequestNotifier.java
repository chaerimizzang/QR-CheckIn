package com.qrcheckin.request;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.qrcheckin.push.PushMessage;
import com.qrcheckin.push.PushService;
import com.qrcheckin.request.RequestDtos.RequestRow;

/** 출석 인정 신청이 오거나 처리되면 휴대폰 알림. 알림이 실패해도 신청 처리는 그대로 성공한다 */
@Component
public class RequestNotifier {

    private static final Logger log = LoggerFactory.getLogger(RequestNotifier.class);

    private final PushService pushService;

    public RequestNotifier(PushService pushService) {
        this.pushService = pushService;
    }

    static String result(com.qrcheckin.attendance.AttendanceStatus s) {
        return switch (s) {
            case PRESENT -> "출석으로";
            case LATE -> "지각으로";
            case EXCUSED -> "공결로";
            case ABSENT -> "결석으로";
        };
    }

    /** 회차 번호 대신 수업 날짜: "11/20(금)" */
    static String day(java.time.LocalDateTime at) {
        return at == null ? "" : at.format(java.time.format.DateTimeFormatter.ofPattern("M/d(E)", java.util.Locale.KOREAN));
    }

    /** 학생이 신청 → 교수에게 */
    public void submitted(RequestRow r, Long professorId) {
        send(List.of(professorId), new PushMessage("출석 인정 신청 [" + r.courseTitle() + "]",
                r.studentName() + " · " + day(r.scheduledAt()) + " 수업",
                "/requests", "request-" + r.id()));
    }

    /** 교수가 승인·반려 → 학생에게 */
    public void reviewed(RequestRow r) {
        boolean ok = r.status() == RequestStatus.APPROVED;
        String body = day(r.scheduledAt()) + " 수업 " + (ok ? "출석 인정됐어요" : "신청이 반려됐어요")
                + (r.reviewComment() != null ? " · " + r.reviewComment() : "");
        send(List.of(r.studentId()), new PushMessage("출석 인정 " + (ok ? "승인" : "반려") + " [" + r.courseTitle() + "]",
                body, "/requests", "request-" + r.id()));
    }

    private void send(List<Long> to, PushMessage message) {
        try {
            pushService.send(to, message);
        } catch (RuntimeException e) {
            log.warn("출석 인정 알림 실패: {}", e.toString());
        }
    }
}
