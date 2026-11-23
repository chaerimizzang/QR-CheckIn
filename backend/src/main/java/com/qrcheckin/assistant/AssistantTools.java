package com.qrcheckin.assistant;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.assistant.AssistantDtos.CourseItem;
import com.qrcheckin.assistant.AssistantDtos.CoursesResult;
import com.qrcheckin.assistant.AssistantDtos.Policy;
import com.qrcheckin.assistant.AssistantDtos.RequestOptionItem;
import com.qrcheckin.assistant.AssistantDtos.RequestOptionsResult;
import com.qrcheckin.assistant.AssistantDtos.SimulationResult;
import com.qrcheckin.assistant.AssistantDtos.SummaryResult;
import com.qrcheckin.assistant.AssistantDtos.UpcomingResult;
import com.qrcheckin.assistant.AssistantDtos.UpcomingSession;
import com.qrcheckin.attendance.AbsenceRule;
import com.qrcheckin.attendance.AttendanceRateResponse.StudentRate;
import com.qrcheckin.attendance.AttendanceRateService;
import com.qrcheckin.attendance.AttendanceStatus;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.Course;
import com.qrcheckin.course.CourseRepository;
import com.qrcheckin.course.Enrollment;
import com.qrcheckin.course.EnrollmentRepository;
import com.qrcheckin.course.Semester;
import com.qrcheckin.profile.StudentProfileResponse.CourseSummary;
import com.qrcheckin.profile.StudentProfileService;
import com.qrcheckin.request.AttendanceRequestService;
import com.qrcheckin.request.RequestDtos.SessionOption;
import com.qrcheckin.schedule.CourseScheduleRepository;
import com.qrcheckin.schedule.ScheduleDtos.ScheduleSlot;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.session.SessionStatus;
import com.qrcheckin.user.User;
import com.qrcheckin.user.UserRepository;

/**
 * AI 출결 도우미의 조회·계산 도구(읽기 전용).
 * - 학생 ID 는 항상 로그인 정보(AuthUser)에서 받는다. LLM 이 다른 학생을 지정할 방법이 없다
 * - 계산은 기존 서비스(출석률·결석 경고·출석 인정)를 그대로 써서 화면과 숫자가 같다
 * - 출결·신청을 바꾸는 기능은 없다
 */
@Service
public class AssistantTools {

    /** 예정 회차를 한 번에 조회할 수 있는 최대 기간(일) */
    static final int MAX_RANGE_DAYS = 31;

    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final ClassSessionRepository sessions;
    private final CourseScheduleRepository schedules;
    private final StudentProfileService profileService;
    private final AttendanceRateService rateService;
    private final AttendanceRequestService requestService;
    private final UserRepository users;
    private final Clock clock;
    private final double failRatio;

    public AssistantTools(EnrollmentRepository enrollments, CourseRepository courses, ClassSessionRepository sessions,
                          CourseScheduleRepository schedules, StudentProfileService profileService,
                          AttendanceRateService rateService, AttendanceRequestService requestService,
                          UserRepository users, Clock clock,
                          @Value("${app.attendance.fail-ratio:0.3333333333}") double failRatio) {
        this.enrollments = enrollments;
        this.courses = courses;
        this.sessions = sessions;
        this.schedules = schedules;
        this.profileService = profileService;
        this.rateService = rateService;
        this.requestService = requestService;
        this.users = users;
        this.clock = clock;
        this.failRatio = failRatio;
    }

    /** 답변 근거 카드에 보여 줄 규칙(서비스 설정값 — 학교 공식 학칙이 아님) */
    public Policy policy() {
        return new Policy(failRatio, AbsenceRule.LATES_PER_ABSENCE,
                "지각 " + AbsenceRule.LATES_PER_ABSENCE + "회 = 결석 1회, " + AbsenceRule.ruleText(failRatio)
                        + " (이 서비스의 출결 규칙)");
    }

    // ---------------- getMyCourses ----------------

    /** 내가 수강하는 수업(기본: 현재 학기만). 수업 이름을 courseId 로 바꿀 때 쓴다 */
    @Transactional(readOnly = true)
    public CoursesResult myCourses(AuthUser student, boolean allSemesters) {
        Semester current = currentSemester();
        List<CourseItem> items = enrollments.findByUserId(student.id()).stream()
                .map(Enrollment::getCourse)
                .map(c -> toItem(c, current))
                .filter(i -> allSemesters || i.currentSemester())
                .sorted(Comparator.comparing(CourseItem::title))
                .toList();
        return new CoursesResult(now(), current.label(), items);
    }

    private CourseItem toItem(Course c, Semester current) {
        Semester s = c.getSemester();
        List<ScheduleSlot> slots = schedules.findByCourseId(c.getId()).stream()
                .map(ScheduleSlot::from)
                .sorted(ScheduleSlot.ORDER)
                .toList();
        return new CourseItem(c.getId(), c.getTitle(),
                s == null ? null : s.key(),
                s == null ? "학기 미정" : s.label(),
                s == null || s.equals(current),
                c.isFinalized(),
                slots);
    }

    // ---------------- getMyAttendanceSummary ----------------

    /** 수업별 출결 요약(출결 현황 화면과 같은 계산). courseId 가 있으면 그 수업만 */
    @Transactional(readOnly = true)
    public SummaryResult attendanceSummary(AuthUser student, Long courseId, boolean allSemesters) {
        String currentKey = currentSemester().key();
        List<CourseSummary> list = profileService.myProfile(student).courses();
        if (courseId != null) {
            list = list.stream().filter(c -> c.courseId().equals(courseId)).toList();
            if (list.isEmpty()) {
                throw new ApiException(ErrorCode.NOT_ENROLLED);
            }
        } else if (!allSemesters) {
            list = list.stream()
                    .filter(c -> c.semesterKey() == null || c.semesterKey().equals(currentKey))
                    .toList();
        }
        return new SummaryResult(now(), policy(), list);
    }

    // ---------------- getMyUpcomingSessions ----------------

    /** from~to(포함) 사이 내 수업 회차. 예비군·병원처럼 날짜로 겹치는 수업을 찾을 때 쓴다 */
    @Transactional(readOnly = true)
    public UpcomingResult sessionsBetween(AuthUser student, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "시작 날짜와 끝 날짜를 알려 주세요");
        }
        if (to.isBefore(from)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "끝 날짜가 시작 날짜보다 빨라요");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "한 번에 " + MAX_RANGE_DAYS + "일까지만 조회할 수 있어요");
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<UpcomingSession> found = new ArrayList<>();
        for (Enrollment e : enrollments.findByUserId(student.id())) {
            Course c = e.getCourse();
            List<ScheduleSlot> slots = schedules.findByCourseId(c.getId()).stream().map(ScheduleSlot::from).toList();
            for (ClassSession s : sessions.findByCourseIdOrderBySessionNoAsc(c.getId())) {
                LocalDateTime at = s.getScheduledAt();
                if (at == null || at.isBefore(start) || !at.isBefore(end)) {
                    continue;
                }
                found.add(new UpcomingSession(c.getId(), c.getTitle(), s.getId(), s.getSessionNo(), at,
                        endOf(at, slots), s.getStatus(), s.isCanceled(), s.getCancelReason()));
            }
        }
        found.sort(Comparator.comparing(UpcomingSession::startAt).thenComparing(UpcomingSession::courseTitle));
        return new UpcomingResult(now(), from, to, found);
    }

    /** 회차 시작 시각과 같은 요일·시작 시간의 주간 시간표가 있으면 그 길이로 끝나는 시각을 구한다 */
    static LocalDateTime endOf(LocalDateTime start, List<ScheduleSlot> slots) {
        return slots.stream()
                .filter(sl -> sl.dayOfWeek() == start.getDayOfWeek() && sl.startTime().equals(start.toLocalTime()))
                .findFirst()
                .map(sl -> start.plusMinutes(sl.durationMinutes()))
                .orElse(null);
    }

    // ---------------- simulateMyAttendance ----------------

    /** "n번 더 지각·결석하면?" — 기록은 바꾸지 않는다 */
    @Transactional(readOnly = true)
    public SimulationResult simulate(AuthUser student, Long courseId, int addLate, int addAbsent) {
        Course course = enrolledCourse(courseId, student);
        User me = users.findById(student.id()).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        StudentRate rate = rateService.rateOfStudent(courseId, me);

        LocalDateTime now = now();
        List<ClassSession> all = sessions.findByCourseIdOrderBySessionNoAsc(courseId);
        int remaining = (int) all.stream().filter(s -> willBeHeld(s, now)).count();

        AttendanceSimulator.Result result;
        try {
            result = AttendanceSimulator.simulate(AttendanceRateService.plannedCount(all), remaining, rate.absentCount(), rate.lateCount(),
                    addLate, addAbsent, failRatio);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, e.getMessage());
        }
        return new SimulationResult(now, policy(), course.getId(), course.getTitle(), result,
                "가정 계산이에요. 실제 출결 기록과 최종 성적은 바뀌지 않아요");
    }

    /**
     * 앞으로 출결이 생길 회차: 마감되지 않았고, 날짜가 지났는데 열지 않은(진행하지 않은) 회차가 아닌 것.
     * (진행하지 않은 회차 판단은 출석 인정 신청과 같다)
     */
    static boolean willBeHeld(ClassSession s, LocalDateTime now) {
        if (s.getStatus() == SessionStatus.CLOSED || s.isCanceled()) {
            return false; // 끝났거나 휴강
        }
        boolean neverHeld = s.getStatus() == SessionStatus.SCHEDULED
                && s.getScheduledAt() != null && s.getScheduledAt().isBefore(now);
        return !neverHeld;
    }

    // ---------------- getMyRequestOptions ----------------

    /** 출석 인정을 신청할 수 있는 회차(신청 화면과 같은 목록)와 신청 화면 바로가기 */
    @Transactional(readOnly = true)
    public RequestOptionsResult requestOptions(AuthUser student, Long courseId) {
        Course course = enrolledCourse(courseId, student);
        boolean finalized = course.isFinalized();
        List<RequestOptionItem> items = requestService.options(courseId, student).stream()
                .map(o -> toOption(courseId, o, finalized))
                .toList();
        return new RequestOptionsResult(now(), course.getId(), course.getTitle(), finalized, items);
    }

    static RequestOptionItem toOption(Long courseId, SessionOption o, boolean finalized) {
        String blocked = finalized ? "출결이 확정된 수업이에요"
                : o.pending() ? "처리 중인 신청이 이미 있어요"
                : o.myStatus() == AttendanceStatus.PRESENT ? "이미 출석이에요"
                : null;
        boolean can = blocked == null;
        return new RequestOptionItem(o.sessionId(), o.sessionNo(), o.scheduledAt(), o.sessionStatus(), o.myStatus(),
                o.pending(), can, blocked, can ? AssistantLinks.requestForm(courseId, o.sessionId()) : null);
    }

    // ---------------- 공통 ----------------

    private Course enrolledCourse(Long courseId, AuthUser student) {
        if (courseId == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "어느 수업인지 알려 주세요");
        }
        // 수강 여부를 먼저 본다 → 없는 수업과 남의 수업을 구분해서 알려 주지 않는다
        if (!enrollments.existsByCourseIdAndUserId(courseId, student.id())) {
            throw new ApiException(ErrorCode.NOT_ENROLLED);
        }
        return courses.findById(courseId).orElseThrow(() -> new ApiException(ErrorCode.NOT_ENROLLED));
    }

    private Semester currentSemester() {
        return Semester.of(LocalDate.now(clock));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
