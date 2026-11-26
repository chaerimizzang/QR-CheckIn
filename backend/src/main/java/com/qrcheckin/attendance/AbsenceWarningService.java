package com.qrcheckin.attendance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.qrcheckin.attendance.AbsenceRule.Level;
import com.qrcheckin.attendance.AttendanceRateResponse.StudentRate;
import com.qrcheckin.course.Course;
import com.qrcheckin.course.CourseRepository;
import com.qrcheckin.push.PushMessage;
import com.qrcheckin.push.PushService;

/**
 * 결석 경고 알림. 주기적으로(기본 2분) 교육마다 수강생의 F 기준 단계를 계산해서,
 * 단계가 올라가면(주의 → 위험 → F) 학생에게만 알린다. 교수는 수강생 출결부의 주의·위험 표시로 확인한다.
 * 단계가 내려가면(출석 인정 승인 등) 기록만 낮추고 알리지 않는다.
 */
@Service
public class AbsenceWarningService {

    private final CourseRepository courses;
    private final AttendanceRateService rates;
    private final AbsenceWarningStateRepository states;
    private final PushService pushService;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AbsenceWarningService(CourseRepository courses, AttendanceRateService rates,
                                 AbsenceWarningStateRepository states, PushService pushService,
                                 TransactionTemplate tx, Clock clock) {
        this.courses = courses;
        this.rates = rates;
        this.states = states;
        this.pushService = pushService;
        this.tx = tx;
        this.clock = clock;
    }

    record Outgoing(Long userId, PushMessage message) {
    }

    @Scheduled(fixedDelayString = "${app.attendance.warning-check-ms:120000}",
            initialDelayString = "${app.attendance.warning-check-ms:120000}")
    public void checkAll() {
        List<Outgoing> out = tx.execute(status -> collect());
        if (out != null) {
            out.forEach(o -> pushService.send(List.of(o.userId()), o.message()));
        }
    }

    private List<Outgoing> collect() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Outgoing> out = new ArrayList<>();
        for (Course course : courses.findAll()) {
            Map<Long, AbsenceWarningState> saved = states.findByCourseId(course.getId()).stream()
                    .collect(Collectors.toMap(AbsenceWarningState::getUserId, Function.identity()));
            for (StudentRate r : rates.ratesOfCourse(course.getId())) {
                Level level = r.warningLevel();
                AbsenceWarningState state = saved.get(r.userId());
                Level before = state == null ? Level.SAFE : state.getLevel();
                if (state == null) {
                    states.save(new AbsenceWarningState(course.getId(), r.userId(), level, now));
                } else if (state.getLevel() != level) {
                    state.update(level, now);
                }
                if (level.ordinal() > before.ordinal() && level.ordinal() >= Level.WARNING.ordinal()) {
                    out.addAll(messages(course, r, level));
                }
            }
        }
        return out;
    }

    private static List<Outgoing> messages(Course course, StudentRate r, Level level) {
        String text = AbsenceRule.message(new AbsenceRule.Status(0, 0, r.remainingAbsences(), level));
        String detail = "인정 결석 " + r.convertedAbsences() + "회 · " + text;
        List<Outgoing> out = new ArrayList<>();
        out.add(new Outgoing(r.userId(), new PushMessage(
                (level == Level.FAIL ? "F 기준 초과 " : "결석 경고 ") + "[" + course.getTitle() + "]", detail,
                "/student/courses/" + course.getId() + "?from=attendance", "warning-" + course.getId())));
        return out;
    }
}
