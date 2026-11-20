package com.qrcheckin.schedule;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.Course;
import com.qrcheckin.course.CourseService;
import com.qrcheckin.schedule.ScheduleDtos.GenerateRequest;
import com.qrcheckin.schedule.ScheduleDtos.GenerateResult;
import com.qrcheckin.schedule.ScheduleDtos.ScheduleRequest;
import com.qrcheckin.schedule.ScheduleDtos.ScheduleSlot;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.session.ClassSessionRepository;

/** 교육 주간 시간표와, 시간표로 회차를 한 번에 만드는 기능 */
@Service
public class ScheduleService {

    /** 한 번에 만들 수 있는 기간(실수로 몇 년치를 만드는 것 방지) */
    static final int MAX_GENERATE_DAYS = 366;
    static final int DEFAULT_RADIUS_M = 50;

    private final CourseScheduleRepository scheduleRepository;
    private final ClassSessionRepository sessionRepository;
    private final CourseService courseService;
    private final Clock clock;
    private final int defaultCheckinMinutes;

    public ScheduleService(CourseScheduleRepository scheduleRepository, ClassSessionRepository sessionRepository,
                           CourseService courseService, Clock clock,
                           @Value("${app.checkin.default-minutes}") int defaultCheckinMinutes) {
        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
        this.courseService = courseService;
        this.clock = clock;
        this.defaultCheckinMinutes = defaultCheckinMinutes;
    }

    /** 시간표 보기 — 교육 주인(관리자)이나 수강생 */
    @Transactional(readOnly = true)
    public List<ScheduleSlot> list(Long courseId, AuthUser user) {
        courseService.get(courseId, user); // 권한 검사(주인 또는 수강생)
        return slots(courseId);
    }

    /** 시간표 전체 교체 — 같은 요일에 시간이 겹치면 거절 */
    @Transactional
    public List<ScheduleSlot> replace(Long courseId, ScheduleRequest req, AuthUser admin) {
        Course course = courseService.getOwnedCourse(courseId, admin);
        List<ScheduleSlot> sorted = req.slots().stream().sorted(ScheduleSlot.ORDER).toList();
        for (ScheduleSlot s : sorted) {
            if (!s.endTime().isAfter(s.startTime())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        dayLabel(s.dayOfWeek()) + "요일 수업이 자정을 넘어가요. 시작 시간이나 길이를 확인해 주세요");
            }
        }
        for (int i = 1; i < sorted.size(); i++) {
            ScheduleSlot prev = sorted.get(i - 1);
            ScheduleSlot cur = sorted.get(i);
            if (prev.dayOfWeek() == cur.dayOfWeek() && cur.startTime().isBefore(prev.endTime())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        dayLabel(cur.dayOfWeek()) + "요일에 시간이 겹치는 수업이 있어요");
            }
        }
        scheduleRepository.deleteByCourseId(courseId);
        scheduleRepository.flush();
        sorted.forEach(s -> scheduleRepository.save(
                new CourseSchedule(course, s.dayOfWeek(), s.startTime(), s.durationMinutes())));
        return sorted;
    }

    /**
     * 시간표대로 기간 안의 회차를 만든다. 같은 시각의 회차가 이미 있으면 건너뛴다(여러 번 눌러도 중복 없음).
     * 장소·반경·출석 시간은 가장 최근에 장소를 정한 회차를 그대로 따라간다(없으면 장소 미지정).
     */
    @Transactional
    public GenerateResult generate(Long courseId, GenerateRequest req, AuthUser admin) {
        Course course = courseService.getOwnedCourse(courseId, admin);
        List<ScheduleSlot> slots = slots(courseId);
        if (slots.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "먼저 주간 시간표를 저장해 주세요");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate from = req != null && req.from() != null ? req.from()
                : later(today, course.getStartDate());
        LocalDate to = req != null && req.to() != null ? req.to() : course.getEndDate();
        if (to == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "끝나는 날짜를 정해 주세요(교육 종료일 또는 직접 입력)");
        }
        if (to.isBefore(from)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "끝나는 날짜가 시작 날짜보다 빨라요");
        }
        if (from.plusDays(MAX_GENERATE_DAYS).isBefore(to)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "한 번에 1년까지만 만들 수 있어요");
        }

        List<ClassSession> existing = sessionRepository.findByCourseIdOrderBySessionNoAsc(courseId);
        Set<LocalDateTime> taken = existing.stream().map(ClassSession::getScheduledAt)
                .collect(Collectors.toCollection(HashSet::new));
        Optional<ClassSession> template = existing.stream()
                .filter(s -> s.getLatitude() != null)
                .max(Comparator.comparingInt(ClassSession::getSessionNo));
        int nextNo = existing.stream().mapToInt(ClassSession::getSessionNo).max().orElse(0) + 1;

        Map<DayOfWeek, List<ScheduleSlot>> byDay = slots.stream()
                .collect(Collectors.groupingBy(ScheduleSlot::dayOfWeek));
        List<ClassSession> created = new ArrayList<>();
        int skipped = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            for (ScheduleSlot slot : byDay.getOrDefault(d.getDayOfWeek(), List.of())) {
                LocalDateTime at = d.atTime(slot.startTime());
                if (!taken.add(at)) {
                    skipped++;
                    continue;
                }
                created.add(new ClassSession(course, nextNo++, at,
                        template.map(ClassSession::getLatitude).orElse(null),
                        template.map(ClassSession::getLongitude).orElse(null),
                        template.map(ClassSession::getRadiusM).orElse(DEFAULT_RADIUS_M),
                        template.map(ClassSession::getCheckinMinutes).orElse(defaultCheckinMinutes)));
            }
        }
        sessionRepository.saveAll(created);
        return new GenerateResult(created.size(), skipped, from, to);
    }

    private List<ScheduleSlot> slots(Long courseId) {
        return scheduleRepository.findByCourseId(courseId).stream()
                .map(ScheduleSlot::from).sorted(ScheduleSlot.ORDER).toList();
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return b == null || a.isAfter(b) ? a : b;
    }

    private static String dayLabel(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }
}
