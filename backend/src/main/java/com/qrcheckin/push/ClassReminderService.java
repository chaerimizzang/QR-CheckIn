package com.qrcheckin.push;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.qrcheckin.course.EnrollmentRepository;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.session.SessionStatus;

/**
 * 수업 시작 전 알림(기본 10분 전). 1분마다 곧 시작하는 회차를 찾아 수강생과 교수에게 웹 푸시를 보낸다.
 * 회차마다 한 번만 보낸다(class_session.reminder_sent_at).
 */
@Service
public class ClassReminderService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final ClassSessionRepository sessions;
    private final EnrollmentRepository enrollments;
    private final PushService pushService;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int leadMinutes;

    public ClassReminderService(ClassSessionRepository sessions, EnrollmentRepository enrollments,
                                PushService pushService, TransactionTemplate tx, Clock clock,
                                @Value("${app.push.lead-minutes:10}") int leadMinutes) {
        this.sessions = sessions;
        this.enrollments = enrollments;
        this.pushService = pushService;
        this.tx = tx;
        this.clock = clock;
        this.leadMinutes = leadMinutes;
    }

    record Outgoing(List<Long> userIds, PushMessage message) {
    }

    @Scheduled(fixedDelayString = "${app.push.check-ms:60000}", initialDelayString = "${app.push.check-ms:60000}")
    public void sendDueReminders() {
        // DB 작업(대상 찾기·보냄 표시)은 트랜잭션 안에서, 실제 전송(네트워크)은 밖에서
        List<Outgoing> outgoing = tx.execute(status -> collectDue());
        if (outgoing != null) {
            outgoing.forEach(o -> pushService.send(o.userIds(), o.message()));
        }
    }

    private List<Outgoing> collectDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Outgoing> out = new ArrayList<>();
        for (ClassSession s : sessions.findByStatusAndScheduledAtGreaterThanAndScheduledAtLessThanEqualAndReminderSentAtIsNull(
                SessionStatus.SCHEDULED, now, now.plusMinutes(leadMinutes))) {
            s.markReminded(now);
            String title = s.getCourse().getTitle();
            long minutes = Math.max(1, (Duration.between(now, s.getScheduledAt()).getSeconds() + 59) / 60);
            String when = s.getSessionNo() + "회차 · " + s.getScheduledAt().format(HM) + " 시작 (" + minutes + "분 뒤)";
            String tag = "class-" + s.getId();

            List<Long> students = enrollments.findByCourseId(s.getCourse().getId()).stream()
                    .map(e -> e.getUser().getId()).toList();
            out.add(new Outgoing(students, new PushMessage("다음 수업이 있어요 [" + title + "]", when,
                    "/student/courses/" + s.getCourse().getId(), tag)));
            out.add(new Outgoing(List.of(s.getCourse().getOwner().getId()), new PushMessage(
                    "다음 수업이 있어요 [" + title + "]", when + " · 출석 시작을 잊지 마세요",
                    "/admin/sessions/" + s.getId(), tag)));
        }
        return out;
    }
}
