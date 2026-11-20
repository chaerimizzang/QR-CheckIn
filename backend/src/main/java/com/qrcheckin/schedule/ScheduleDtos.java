package com.qrcheckin.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 주간 시간표·회차 자동 생성 요청/응답 */
public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    /** 시간표 한 칸. startTime 은 "09:00" 형식, 수업 길이는 10분~10시간 */
    public record ScheduleSlot(
            @NotNull(message = "요일을 골라 주세요") DayOfWeek dayOfWeek,
            @NotNull(message = "시작 시간을 넣어 주세요") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @Min(value = 10, message = "수업 길이는 10분 이상이어야 해요")
            @Max(value = 600, message = "수업 길이는 10시간 이하여야 해요") int durationMinutes) {

        public static ScheduleSlot from(CourseSchedule s) {
            return new ScheduleSlot(s.getDayOfWeek(), s.getStartTime(), s.getDurationMinutes());
        }

        public LocalTime endTime() {
            return startTime.plusMinutes(durationMinutes);
        }

        /** 월요일부터, 같은 요일이면 이른 시간부터 */
        public static final Comparator<ScheduleSlot> ORDER =
                Comparator.comparing(ScheduleSlot::dayOfWeek).thenComparing(ScheduleSlot::startTime);
    }

    /** 평일(월~금)만 수업 요일로 쓸 수 있다 */
    public static boolean isWeekday(DayOfWeek d) {
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
    }

    /**
     * 교육 만들 때 쓰는 간단한 주간 계획: 고른 요일(월~금)마다 같은 시간(시작~끝)에 수업.
     * 예: {"days":["MONDAY","WEDNESDAY"],"startTime":"09:00","endTime":"10:30"}
     */
    public record WeeklyPlan(
            @NotNull @Size(min = 1, max = 5, message = "수업 요일을 1개 이상 골라 주세요") List<@NotNull DayOfWeek> days,
            @NotNull(message = "시작 시간을 넣어 주세요") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @NotNull(message = "끝나는 시간을 넣어 주세요") @JsonFormat(pattern = "HH:mm") LocalTime endTime) {

        /** 요일마다 한 칸씩 — 길이 = 끝 - 시작 */
        public List<ScheduleSlot> toSlots() {
            int minutes = (int) java.time.Duration.between(startTime, endTime).toMinutes();
            return days.stream().distinct().sorted()
                    .map(d -> new ScheduleSlot(d, startTime, minutes)).toList();
        }
    }

    /** 시간표 전체 교체(비우면 시간표 삭제) */
    public record ScheduleRequest(
            @NotNull @Size(max = 14, message = "시간표는 14칸까지 넣을 수 있어요") List<@Valid ScheduleSlot> slots) {
    }

    /** 회차 자동 생성 기간. 비우면 from = 오늘과 교육 시작일 중 늦은 날, to = 교육 종료일 */
    public record GenerateRequest(LocalDate from, LocalDate to) {
    }

    /**
     * @param created 새로 만든 회차 수
     * @param skipped 같은 시각의 회차가 이미 있어서 건너뛴 수
     */
    public record GenerateResult(int created, int skipped, LocalDate from, LocalDate to) {
    }
}
