package com.qrcheckin.config;

import java.sql.Connection;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.qrcheckin.attendance.Attendance;
import com.qrcheckin.attendance.AttendanceRepository;
import com.qrcheckin.attendance.AttendanceStatus;
import com.qrcheckin.course.Course;
import com.qrcheckin.course.CourseRepository;
import com.qrcheckin.course.Enrollment;
import com.qrcheckin.course.EnrollmentRepository;
import com.qrcheckin.course.Semester;
import com.qrcheckin.course.SemesterTerm;
import com.qrcheckin.request.AttendanceRequest;
import com.qrcheckin.request.AttendanceRequestRepository;
import com.qrcheckin.request.RequestType;
import com.qrcheckin.schedule.CourseSchedule;
import com.qrcheckin.schedule.CourseScheduleRepository;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.user.Role;
import com.qrcheckin.user.User;
import com.qrcheckin.user.UserRepository;

/**
 * 발표 시연용 더미 데이터. <b>DB 의 데이터를 전부 지우고</b> 다시 채운 뒤 프로그램을 끝낸다(VAPID 키 app_setting 은 남김).
 * 평소에는 실행되지 않는다 — 프로필 demo-seed 를 직접 켤 때만:
 * <pre>
 *   docker compose build app
 *   docker compose stop app
 *   docker compose run --rm -e SPRING_PROFILES_ACTIVE=mysql,demo-seed app
 *   docker compose up -d
 * </pre>
 * 계정 비밀번호는 모두 {@value #PASSWORD}. 교수 prof1·prof2@demo.kr, 학생 student01~30@demo.kr
 */
@Component
@Profile("demo-seed")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String PASSWORD = "demo1234";

    /** 지우는 표(자식 → 부모 순서). app_setting(웹 푸시 VAPID 키)은 남긴다 */
    private static final List<String> TABLES = List.of(
            "request_evidence", "attendance_request", "attendance_history", "attendance",
            "absence_warning_state", "push_subscription", "class_session", "course_schedule",
            "enrollment", "course", "users");

    /** 국립부경대 대연캠퍼스 근처(시연용 기준점) */
    private static final double LAT = 35.1337;
    private static final double LNG = 129.1030;

    private static final String[] NAMES = {
        "김민준", "이서연", "박지호", "최수아", "정예준", "강하은", "조도윤", "윤지우", "장시우", "임서윤",
        "한주원", "오하린", "서건우", "신채원", "권현우", "황지안", "안유준", "송다은", "전민서", "홍승우",
        "고은서", "문태윤", "양소율", "배준서", "백나연", "허지환", "남윤아", "노시현", "하예린", "구민재"};

    private final DataSource dataSource;
    private final TransactionTemplate tx;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final ConfigurableApplicationContext context;
    private final UserRepository users;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final CourseScheduleRepository schedules;
    private final ClassSessionRepository sessions;
    private final AttendanceRepository attendances;
    private final AttendanceRequestRepository requests;
    private final boolean exitAfter;

    public DemoDataSeeder(DataSource dataSource, TransactionTemplate tx, PasswordEncoder passwordEncoder, Clock clock,
                          ConfigurableApplicationContext context, UserRepository users, CourseRepository courses,
                          EnrollmentRepository enrollments, CourseScheduleRepository schedules,
                          ClassSessionRepository sessions, AttendanceRepository attendances,
                          AttendanceRequestRepository requests,
                          @Value("${app.demo.exit-after:true}") boolean exitAfter) {
        this.dataSource = dataSource;
        this.tx = tx;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.context = context;
        this.users = users;
        this.courses = courses;
        this.enrollments = enrollments;
        this.schedules = schedules;
        this.sessions = sessions;
        this.attendances = attendances;
        this.requests = requests;
        this.exitAfter = exitAfter;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.warn("[demo-seed] 데이터를 모두 지우고 시연용 데이터를 넣습니다");
        wipe();
        tx.executeWithoutResult(status -> seed());
        log.warn("[demo-seed] 끝 — 교수 prof1@demo.kr·prof2@demo.kr, 학생 student01~30@demo.kr, 비밀번호 {}", PASSWORD);
        if (exitAfter) { // 테스트에서는 끄지 않는다(app.demo.exit-after=false)
            int code = SpringApplication.exit(context, () -> 0);
            System.exit(code);
        }
    }

    /** MySQL·H2 모두: 외래 키 검사를 잠시 끄고 표를 비운다(번호도 1부터) */
    void wipe() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String product;
        try (Connection c = dataSource.getConnection()) {
            product = c.getMetaData().getDatabaseProductName().toLowerCase();
        }
        boolean h2 = product.contains("h2");
        jdbc.execute(h2 ? "SET REFERENTIAL_INTEGRITY FALSE" : "SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String t : TABLES) {
                try {
                    jdbc.execute(h2 ? "TRUNCATE TABLE " + t + " RESTART IDENTITY" : "TRUNCATE TABLE " + t);
                } catch (Exception e) {
                    log.warn("[demo-seed] {} 비우기 건너뜀: {}", t, e.getMessage());
                }
            }
        } finally {
            jdbc.execute(h2 ? "SET REFERENTIAL_INTEGRITY TRUE" : "SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    void seed() {
        LocalDateTime now = LocalDateTime.now(clock);
        Random rnd = new Random(20261124L); // 매번 같은 데이터
        String hash = passwordEncoder.encode(PASSWORD);

        User prof1 = admin("prof1@demo.kr", "김교수", "컴퓨터인공지능공학과", hash, now);
        User prof2 = admin("prof2@demo.kr", "이교수", "컴퓨터인공지능공학과", hash, now);
        List<User> students = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) {
            User u = new User(String.format("student%02d@demo.kr", i + 1), hash, NAMES[i],
                    String.valueOf(202412001 + i), Role.STUDENT, now);
            u.changeDepartment(i % 5 == 4 ? "정보통신공학과" : "컴퓨터인공지능공학과");
            students.add(users.save(u));
        }

        // 2026년 2학기: 9/1(화) 개강, 14주
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = start.plusWeeks(14).minusDays(1);
        Course swe = course(prof1, "소프트웨어공학", "공학관 302호", start, end,
                List.of(slot(DayOfWeek.MONDAY, 13, 0, 75)), students.subList(0, 30));
        Course ds = course(prof1, "자료구조", "공학관 401호", start, end,
                List.of(slot(DayOfWeek.TUESDAY, 13, 0, 75), slot(DayOfWeek.THURSDAY, 13, 0, 75)), students.subList(0, 25));
        List<User> dbStudents = new ArrayList<>(students.subList(0, 20));
        dbStudents.addAll(students.subList(25, 30));
        Course db = course(prof2, "데이터베이스", "정보관 105호", start, end,
                List.of(slot(DayOfWeek.WEDNESDAY, 10, 0, 90)), dbStudents);

        // 휴강: 추석 연휴(9/24 목), 학회 참석(10/21 수), 개교기념 행사(11/16 월)
        Map<LocalDate, String> canceled = Map.of(
                LocalDate.of(2026, 9, 24), "추석 연휴",
                LocalDate.of(2026, 10, 21), "학회 참석",
                LocalDate.of(2026, 11, 16), "개교기념 행사");

        // 결석을 일부러 많이 만든 학생(시연: 주의·위험·F 기준 초과) — 과목별 결석 수
        Map<String, Integer> forcedAbsent = new HashMap<>();
        forcedAbsent.put(key(swe, students.get(4)), 3);   // 5번 학생: 주의(남은 결석 1)
        forcedAbsent.put(key(swe, students.get(11)), 4);  // 12번 학생: 위험(남은 결석 0)
        forcedAbsent.put(key(swe, students.get(19)), 6);  // 20번 학생: F 기준 초과
        forcedAbsent.put(key(ds, students.get(7)), 7);    // 8번 학생: 자료구조 주의
        forcedAbsent.put(key(db, students.get(2)), 3);    // 3번 학생: 데이터베이스 주의

        Map<Course, List<ClassSession>> byCourse = new HashMap<>();
        for (Course c : List.of(swe, ds, db)) {
            byCourse.put(c, sessionsOf(c, start, end, canceled, now));
        }

        for (Course c : List.of(swe, ds, db)) {
            List<ClassSession> closed = byCourse.get(c).stream()
                    .filter(s -> s.getStatus() == com.qrcheckin.session.SessionStatus.CLOSED).toList();
            for (Enrollment e : enrollments.findAll()) {
                if (!e.getCourse().getId().equals(c.getId())) continue;
                User u = e.getUser();
                int forced = forcedAbsent.getOrDefault(key(c, u), 0);
                List<Integer> absentIdx = pick(rnd, closed.size(), forced);
                for (int i = 0; i < closed.size(); i++) {
                    ClassSession s = closed.get(i);
                    AttendanceStatus st;
                    if (absentIdx.contains(i)) st = AttendanceStatus.ABSENT;
                    else {
                        double r = rnd.nextDouble();
                        st = r < 0.03 ? AttendanceStatus.ABSENT : r < 0.08 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
                    }
                    attendances.save(attendanceOf(s, u, st, rnd));
                }
            }
        }

        // 시연 학생 student01(김민준): 자료구조 결석 1번은 출석 인정으로 처리됨, 다음 주 예비군은 신청 대기
        User main = students.get(0);
        List<ClassSession> dsClosed = byCourse.get(ds).stream()
                .filter(s -> s.getStatus() == com.qrcheckin.session.SessionStatus.CLOSED).toList();
        ClassSession sick = dsClosed.get(Math.min(5, dsClosed.size() - 1));
        Attendance a = attendances.findBySessionIdAndUserId(sick.getId(), main.getId()).orElseThrow();
        AttendanceStatus before = a.getStatus() == AttendanceStatus.PRESENT ? AttendanceStatus.ABSENT : a.getStatus();
        a.changeByAdmin(AttendanceStatus.PRESENT, sick.getScheduledAt().plusDays(2));
        AttendanceRequest approved = new AttendanceRequest(sick, main, RequestType.EXCUSE, AttendanceStatus.PRESENT, before,
                "병원 진료(진료확인서 제출)", false, sick.getScheduledAt().plusDays(1));
        approved.approve(prof1, AttendanceStatus.PRESENT, "확인했어요", sick.getScheduledAt().plusDays(2));
        requests.save(approved);

        ClassSession nextDs = byCourse.get(ds).stream()
                .filter(s -> s.getStatus() == com.qrcheckin.session.SessionStatus.SCHEDULED && s.getScheduledAt().isAfter(now.plusDays(2)))
                .findFirst().orElse(null);
        if (nextDs != null) {
            requests.save(new AttendanceRequest(nextDs, main, RequestType.EXCUSE, AttendanceStatus.PRESENT, null,
                    "예비군 훈련 소집", false, now.minusHours(3)));
        }

        // 교수 화면용: 대기 신청 2건, 반려 1건
        pendingFor(swe, students.get(4), byCourse, "가족 경조사(조모상)", now.minusDays(1));
        pendingFor(db, students.get(2), byCourse, "교내 학술 행사 참가", now.minusHours(20));
        AttendanceRequest rejected = requestFor(swe, students.get(11), byCourse, "늦잠", now.minusDays(4));
        if (rejected != null) {
            rejected.reject(prof1, "출석 인정 사유에 해당하지 않아요", now.minusDays(3));
            requests.save(rejected);
        }
        log.warn("[demo-seed] 교수 2, 학생 {}, 과목 3, 회차 {}", students.size(),
                byCourse.values().stream().mapToInt(List::size).sum());
    }

    private User admin(String email, String name, String dept, String hash, LocalDateTime now) {
        User u = new User(email, hash, name, null, Role.ADMIN, now);
        u.changeDepartment(dept);
        return users.save(u);
    }

    private record Slot(DayOfWeek day, LocalTime start, int minutes) { }

    private static Slot slot(DayOfWeek d, int h, int m, int minutes) {
        return new Slot(d, LocalTime.of(h, m), minutes);
    }

    private final Map<Long, List<Slot>> slotsOf = new HashMap<>();

    private Course course(User owner, String title, String room, LocalDate start, LocalDate end,
                          List<Slot> slots, List<User> members) {
        Course c = new Course(owner, title, start, end);
        c.changeRoom(room);
        c.setSemester(new Semester(2026, SemesterTerm.FALL));
        c = courses.save(c);
        for (Slot s : slots) {
            schedules.save(new CourseSchedule(c, s.day(), s.start(), s.minutes()));
        }
        for (User u : members) {
            enrollments.save(new Enrollment(c, u));
        }
        slotsOf.put(c.getId(), slots);
        return c;
    }

    /** 개강일부터 매주 회차. 지난 수업은 출석을 받고 마감, 휴강 날은 휴강, 남은 수업은 예정 */
    private List<ClassSession> sessionsOf(Course c, LocalDate start, LocalDate end, Map<LocalDate, String> canceled,
                                          LocalDateTime now) {
        List<LocalDateTime> times = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            for (Slot s : slotsOf.get(c.getId())) {
                if (d.getDayOfWeek() == s.day()) times.add(d.atTime(s.start()));
            }
        }
        times.sort(null);
        List<ClassSession> out = new ArrayList<>();
        int no = 1;
        for (LocalDateTime t : times) {
            ClassSession s = new ClassSession(c, no++, t, LAT, LNG, 50, 10);
            String reason = canceled.get(t.toLocalDate());
            if (reason != null) {
                s.cancel(reason, t.minusDays(3));
            } else if (t.plusMinutes(10).isBefore(now)) {
                s.open(t, "demo-" + no);
                s.close(t.plusMinutes(10));
            }
            out.add(sessions.save(s));
        }
        return out;
    }

    private static Attendance attendanceOf(ClassSession s, User u, AttendanceStatus st, Random rnd) {
        LocalDateTime t = s.getScheduledAt();
        return switch (st) {
            case PRESENT -> Attendance.byQr(s, u, t.minusMinutes(rnd.nextInt(5)).plusSeconds(rnd.nextInt(60)), "demo-device-" + u.getId());
            case LATE -> Attendance.manual(s, u, AttendanceStatus.LATE, t.plusMinutes(6 + rnd.nextInt(8)));
            default -> Attendance.absentBySystem(s, u, t.plusMinutes(10));
        };
    }

    private void pendingFor(Course c, User u, Map<Course, List<ClassSession>> byCourse, String reason, LocalDateTime at) {
        AttendanceRequest r = requestFor(c, u, byCourse, reason, at);
        if (r != null) requests.save(r);
    }

    /** 그 학생이 결석한 가장 최근 회차로 신청을 만든다 */
    private AttendanceRequest requestFor(Course c, User u, Map<Course, List<ClassSession>> byCourse, String reason,
                                         LocalDateTime at) {
        List<ClassSession> list = new ArrayList<>(byCourse.get(c));
        java.util.Collections.reverse(list);
        for (ClassSession s : list) {
            if (s.getStatus() != com.qrcheckin.session.SessionStatus.CLOSED) continue;
            var a = attendances.findBySessionIdAndUserId(s.getId(), u.getId());
            if (a.isPresent() && a.get().getStatus() == AttendanceStatus.ABSENT) {
                return new AttendanceRequest(s, u, RequestType.EXCUSE, AttendanceStatus.PRESENT, AttendanceStatus.ABSENT,
                        reason, false, at);
            }
        }
        return null;
    }

    private static String key(Course c, User u) {
        return c.getId() + ":" + u.getId();
    }

    /** 0..n-1 중 k개를 고른다 */
    private static List<Integer> pick(Random rnd, int n, int k) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < n; i++) all.add(i);
        java.util.Collections.shuffle(all, rnd);
        return all.subList(0, Math.min(k, n));
    }
}
