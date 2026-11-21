package com.qrcheckin.course;

import java.util.List;

import com.qrcheckin.schedule.ScheduleDtos.ScheduleSlot;
import com.qrcheckin.schedule.ScheduleDtos.WeeklyPlan;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.qrcheckin.user.User;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CourseDtos {

    private CourseDtos() {
    }

    /**
     * B-02 교육 생성·수정.
     * schedules(선택, 만들 때만 사용): [수업 요일(월~금) + 시작~끝 시간] 묶음 여러 개
     *   → 시간표 저장 + 오늘부터 종료일까지 회차 자동 생성. 예: 월·수 09:00~10:30 + 금 13:00~15:00
     */
    public record CourseRequest(
            @NotBlank @Size(max = 100) String title,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 10, message = "수업 시간은 10개까지 넣을 수 있어요") List<@Valid WeeklyPlan> schedules,
            /** 학기(선택) — 비우면 시작일(없으면 오늘)로 정한다 */
            Integer semesterYear,
            SemesterTerm semesterTerm) {
    }

    /** schedules: 주간 시간표(월요일부터 정렬) */
    public record CourseResponse(Long id, String title, LocalDate startDate, LocalDate endDate,
                                 String ownerName, long studentCount, List<ScheduleSlot> schedules,
                                 Integer semesterYear, SemesterTerm semesterTerm, String semesterKey,
                                 String semesterLabel, LocalDateTime finalizedAt) {
        public static CourseResponse of(Course c, long studentCount, List<ScheduleSlot> schedules) {
            Semester s = c.getSemester();
            return new CourseResponse(c.getId(), c.getTitle(), c.getStartDate(), c.getEndDate(),
                    c.getOwner().getName(), studentCount, schedules,
                    s == null ? null : s.year(), s == null ? null : s.term(),
                    s == null ? null : s.key(), s == null ? "학기 미정" : s.label(), c.getFinalizedAt());
        }
    }

    /** 학기 선택 목록용 */
    public record SemesterResponse(int year, SemesterTerm term, String key, String label) {
        public static SemesterResponse of(Semester s) {
            return new SemesterResponse(s.year(), s.term(), s.key(), s.label());
        }
    }

    /** 출결 확정 전 확인: 막는 이유가 없으면 confirm 에 지워질 예정 회차 수 등을 알려 준다 */
    public record FinalizeCheck(long openSessions, long pendingRequests, long unopenedSessions) {
    }

    /** B-04 수강생 추가 — 가입한 계정의 이메일 */
    public record EnrollmentRequest(@NotBlank @Email String email) {
    }

    public record StudentResponse(Long userId, String email, String name, String studentNo) {
        public static StudentResponse from(User u) {
            return new StudentResponse(u.getId(), u.getEmail(), u.getName(), u.getStudentNo());
        }
    }
}
