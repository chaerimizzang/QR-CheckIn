package com.qrcheckin.request;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.qrcheckin.attendance.Attendance;
import com.qrcheckin.attendance.AttendanceRepository;
import com.qrcheckin.attendance.AttendanceService;
import com.qrcheckin.attendance.AttendanceStatus;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.CourseRepository;
import com.qrcheckin.course.EnrollmentRepository;
import com.qrcheckin.request.RequestDtos.RequestRow;
import com.qrcheckin.request.RequestDtos.SessionOption;
import com.qrcheckin.session.ClassSession;
import com.qrcheckin.session.ClassSessionRepository;
import com.qrcheckin.session.SessionStatus;
import com.qrcheckin.user.User;
import com.qrcheckin.user.UserRepository;

/**
 * 출석 인정 신청. 학생은 수업 날짜와 사유(증빙 선택)만 내고, 교수가 인정하면 그 회차가 출석이 된다.
 * - 결석·지각·미정 회차, 또는 아직 열리지 않은 회차(예비군처럼 미리 아는 경우)에 신청할 수 있다
 * - 날짜가 지났는데 열지 않은(진행하지 않은) 회차는 신청할 수 없다
 * 인정하면 교수가 직접 바꾼 것과 똑같이 출결이 바뀌고 수정 이력이 남는다.
 */
@Service
public class AttendanceRequestService {

    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> FILE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic",
            "image/heif", "image/gif", "application/pdf");

    private final AttendanceRequestRepository requests;
    private final RequestEvidenceRepository evidences;
    private final ClassSessionRepository sessions;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final AttendanceRepository attendances;
    private final AttendanceService attendanceService;
    private final UserRepository users;
    private final Clock clock;

    public AttendanceRequestService(AttendanceRequestRepository requests, RequestEvidenceRepository evidences,
                                    ClassSessionRepository sessions, CourseRepository courses, EnrollmentRepository enrollments,
                                    AttendanceRepository attendances, AttendanceService attendanceService,
                                    UserRepository users, Clock clock) {
        this.requests = requests;
        this.evidences = evidences;
        this.sessions = sessions;
        this.courses = courses;
        this.enrollments = enrollments;
        this.attendances = attendances;
        this.attendanceService = attendanceService;
        this.users = users;
        this.clock = clock;
    }

    // ---------------- 학생 ----------------

    /** 신청 화면: 이 교육에서 내가 신청할 수 있는 회차와 지금 상태 */
    @Transactional(readOnly = true)
    public List<SessionOption> options(Long courseId, AuthUser student) {
        requireEnrolled(courseId, student.id());
        Map<Long, AttendanceStatus> mine = new java.util.HashMap<>();
        for (Attendance a : attendances.findByUserIdAndSessionCourseIdOrderBySessionSessionNoAsc(student.id(), courseId)) {
            mine.put(a.getSession().getId(), a.getStatus());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return sessions.findByCourseIdOrderBySessionNoAsc(courseId).stream()
                .filter(s -> !neverHeld(s, now)) // 날짜가 지났는데 한 번도 열지 않은 회차는 수업이 없었던 것
                .filter(s -> !s.isCanceled()) // 휴강은 신청할 필요가 없다
                .map(s -> new SessionOption(s.getId(), s.getSessionNo(), s.getScheduledAt(), s.getStatus(),
                        mine.get(s.getId()),
                        requests.existsBySessionIdAndStudentIdAndStatus(s.getId(), student.id(), RequestStatus.PENDING)))
                .toList();
    }

    @Transactional
    public RequestRow submit(Long sessionId, AuthUser student, RequestType type, AttendanceStatus requested,
                             String reason, MultipartFile file) {
        ClassSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없어요"));
        requireEnrolled(session.getCourse().getId(), student.id());
        if (session.getCourse().isFinalized()) {
            throw new ApiException(ErrorCode.COURSE_FINALIZED, "출결이 확정된 수업이라 신청할 수 없어요");
        }
        if (session.isCanceled()) {
            throw new ApiException(ErrorCode.SESSION_CANCELED, "휴강한 수업이라 신청하지 않아도 돼요");
        }
        String text = reason == null ? "" : reason.strip();
        if (text.isEmpty() || text.length() > 500) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "사유를 1~500자로 입력해 주세요");
        }

        if (neverHeld(session, LocalDateTime.now(clock))) {
            throw notAllowed("진행하지 않은 수업이에요");
        }
        AttendanceStatus current = attendances.findBySessionIdAndUserId(sessionId, student.id())
                .map(Attendance::getStatus).orElse(null);
        AttendanceStatus target = targetStatus(type, requested, session, current);

        if (requests.existsBySessionIdAndStudentIdAndStatus(sessionId, student.id(), RequestStatus.PENDING)) {
            throw new ApiException(ErrorCode.REQUEST_DUPLICATED);
        }

        boolean hasFile = file != null && !file.isEmpty();
        if (hasFile) {
            validateFile(file);
        }
        User me = users.getReferenceById(student.id());
        AttendanceRequest saved = requests.save(new AttendanceRequest(session, me,
                type == null ? RequestType.EXCUSE : type, target, current, text, hasFile,
                LocalDateTime.now(clock)));
        if (hasFile) {
            try {
                evidences.save(new RequestEvidence(saved.getId(), safeName(file.getOriginalFilename()),
                        file.getContentType(), file.getBytes()));
            } catch (IOException e) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "파일을 읽지 못했어요. 다시 올려 주세요");
            }
        }
        return RequestRow.from(requests.findById(saved.getId()).orElseThrow());
    }

    /** 인정되면 출석이 된다. 이미 출석이면 신청할 필요가 없다 */
    static AttendanceStatus targetStatus(RequestType type, AttendanceStatus requested, ClassSession session,
                                         AttendanceStatus current) {
        if (current == AttendanceStatus.PRESENT) {
            throw notAllowed("이미 출석 처리된 수업이에요");
        }
        return AttendanceStatus.PRESENT;
    }

    @Transactional(readOnly = true)
    public List<RequestRow> mine(AuthUser student) {
        return requests.findByStudentIdOrderByCreatedAtDescIdDesc(student.id()).stream().map(RequestRow::from).toList();
    }

    @Transactional
    public RequestRow cancel(Long requestId, AuthUser student) {
        AttendanceRequest r = load(requestId);
        if (!r.getStudent().getId().equals(student.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        requirePending(r);
        r.cancel(LocalDateTime.now(clock));
        return RequestRow.from(r);
    }

    // ---------------- 교수 ----------------

    @Transactional(readOnly = true)
    public List<RequestRow> forProfessor(AuthUser admin) {
        return requests.findBySessionCourseOwnerIdOrderByCreatedAtDescIdDesc(admin.id()).stream()
                .map(RequestRow::from).toList();
    }

    @Transactional(readOnly = true)
    public long pendingCount(AuthUser admin) {
        return requests.countBySessionCourseOwnerIdAndStatus(admin.id(), RequestStatus.PENDING);
    }

    /** 인정: 그 회차를 출석으로 바꾼다 */
    @Transactional
    public RequestRow approve(Long requestId, AuthUser admin, String comment) {
        AttendanceRequest r = ownedByProfessor(requestId, admin);
        requirePending(r);
        // 교수가 직접 바꾸는 것과 같은 경로 → 수정 이력이 남고, 마감 스케줄러가 덮어쓰지 않는다
        attendanceService.changeStatus(r.getSession().getId(), r.getStudent().getId(), AttendanceStatus.PRESENT, admin);
        r.approve(users.getReferenceById(admin.id()), AttendanceStatus.PRESENT, blankToNull(comment),
                LocalDateTime.now(clock));
        return RequestRow.from(r);
    }

    @Transactional
    public RequestRow reject(Long requestId, AuthUser admin, String comment) {
        AttendanceRequest r = ownedByProfessor(requestId, admin);
        requirePending(r);
        r.getSession().getCourse().requireEditable();
        String why = blankToNull(comment);
        if (why == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "반려 사유를 적어 주세요");
        }
        r.reject(users.getReferenceById(admin.id()), why, LocalDateTime.now(clock));
        return RequestRow.from(r);
    }

    // ---------------- 공통 ----------------

    /** 증빙 파일: 신청한 학생 본인과 그 교육의 교수만 볼 수 있다 */
    @Transactional(readOnly = true)
    public RequestEvidence evidence(Long requestId, AuthUser user) {
        AttendanceRequest r = load(requestId);
        boolean mine = r.getStudent().getId().equals(user.id());
        boolean owner = r.getSession().getCourse().getOwner().getId().equals(user.id());
        if (!mine && !owner) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        RequestEvidence e = evidences.findById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "첨부한 파일이 없어요"));
        if (e.getData() == null) { // 트랜잭션 안에서 파일 내용을 읽어 둔다
            throw new ApiException(ErrorCode.NOT_FOUND, "첨부한 파일이 없어요");
        }
        return e;
    }

    /** 교육 담당 교수 id(알림 보낼 때) */
    @Transactional(readOnly = true)
    public Long professorOfCourse(Long courseId) {
        return courses.findById(courseId).map(c -> c.getOwner().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** 신청 한 건(알림 보낼 때 등) */
    @Transactional(readOnly = true)
    public Optional<RequestRow> find(Long requestId) {
        return requests.findById(requestId).map(RequestRow::from);
    }

    private AttendanceRequest load(Long id) {
        return requests.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "신청을 찾을 수 없어요"));
    }

    private AttendanceRequest ownedByProfessor(Long id, AuthUser admin) {
        AttendanceRequest r = load(id);
        if (!r.getSession().getCourse().getOwner().getId().equals(admin.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "내 교육의 신청이 아니에요");
        }
        return r;
    }

    private void requireEnrolled(Long courseId, Long userId) {
        if (!enrollments.existsByCourseIdAndUserId(courseId, userId)) {
            throw new ApiException(ErrorCode.NOT_ENROLLED);
        }
    }

    private static void requirePending(AttendanceRequest r) {
        if (r.getStatus() != RequestStatus.PENDING) {
            throw new ApiException(ErrorCode.REQUEST_ALREADY_DONE);
        }
    }

    private static void validateFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!FILE_TYPES.contains(type)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "사진(JPG·PNG 등) 또는 PDF 파일만 올릴 수 있어요");
        }
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "증빙";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\r\\n\"]", "_");
        return base.length() > 150 ? base.substring(base.length() - 150) : base;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** 예정 상태인데 수업 시간이 이미 지남 = 출석을 열지 않은(진행하지 않은) 회차 */
    static boolean neverHeld(ClassSession s, LocalDateTime now) {
        return s.getStatus() == SessionStatus.SCHEDULED && s.getScheduledAt() != null && s.getScheduledAt().isBefore(now);
    }

    private static ApiException notAllowed(String message) {
        return new ApiException(ErrorCode.REQUEST_NOT_ALLOWED, message);
    }
}
