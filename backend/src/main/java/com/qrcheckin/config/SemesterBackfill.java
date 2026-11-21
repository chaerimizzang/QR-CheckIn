package com.qrcheckin.config;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.qrcheckin.course.Course;
import com.qrcheckin.course.CourseRepository;
import com.qrcheckin.course.Semester;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.session.ClassSessionRepository;

/**
 * 학기 기능 전에 만든 교육에 학기를 채운다(한 번, 비어 있는 것만).
 * 시작일 → 첫 회차 날짜 → 오늘 순서로 정한다.
 */
@Component
@Order(1)
public class SemesterBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SemesterBackfill.class);

    private final CourseRepository courses;
    private final ClassSessionRepository sessions;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SemesterBackfill(CourseRepository courses, ClassSessionRepository sessions, TransactionTemplate tx,
                            Clock clock) {
        this.courses = courses;
        this.sessions = sessions;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer filled = tx.execute(status -> {
            int n = 0;
            for (Course c : courses.findAll()) {
                if (c.getSemester() != null) {
                    continue;
                }
                LocalDate base = c.getStartDate();
                if (base == null) {
                    base = sessions.findByCourseIdOrderBySessionNoAsc(c.getId()).stream()
                            .map(ClassSession::getScheduledAt).filter(java.util.Objects::nonNull)
                            .map(java.time.LocalDateTime::toLocalDate).min(LocalDate::compareTo)
                            .orElse(LocalDate.now(clock));
                }
                c.setSemester(Semester.of(base));
                n++;
            }
            return n;
        });
        if (filled != null && filled > 0) {
            log.info("학기가 비어 있던 교육 {}개에 학기를 채웠어요", filled);
        }
    }
}
