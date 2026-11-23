package com.qrcheckin.schedule;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.course.CourseDtos.CourseRequest;
import com.qrcheckin.course.CourseDtos.CourseResponse;
import com.qrcheckin.course.CourseService;
import com.qrcheckin.schedule.ScheduleDtos.GenerateRequest;
import com.qrcheckin.schedule.ScheduleDtos.ScheduleRequest;
import com.qrcheckin.schedule.ScheduleDtos.ScheduleSlot;
import com.qrcheckin.schedule.ScheduleDtos.WeeklyPlan;

/**
 * 교육 만들기 한 번으로: 교육 + 주간 시간표 + 회차 자동 생성(오늘부터 종료일까지).
 * 중간에 실패하면 전부 취소된다(한 트랜잭션).
 */
@Service
public class CourseSetupService {

    private final CourseService courseService;
    private final ScheduleService scheduleService;

    public CourseSetupService(CourseService courseService, ScheduleService scheduleService) {
        this.courseService = courseService;
        this.scheduleService = scheduleService;
    }

    @Transactional
    public CourseResponse create(CourseRequest req, AuthUser admin) {
        List<WeeklyPlan> plans = req.schedules() == null ? List.of() : req.schedules();
        if (!plans.isEmpty()) {
            if (req.startDate() == null || req.endDate() == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "수업 기간(몇 월부터 몇 월까지)을 넣어 주세요");
            }
            for (WeeklyPlan plan : plans) {
                if (!plan.endTime().isAfter(plan.startTime())) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "끝나는 시간이 시작 시간보다 늦어야 해요");
                }
            }
        }
        CourseResponse created = courseService.create(req, admin);
        if (plans.isEmpty()) {
            return created;
        }
        // 묶음들을 요일별 칸으로 펼쳐서 저장 — 같은 요일에 시간이 겹치면 replace 에서 거절(전부 취소)
        List<ScheduleSlot> slots = plans.stream().flatMap(p -> p.toSlots().stream()).toList();
        scheduleService.replace(created.id(), new ScheduleRequest(slots), admin);
        // 교육을 만들 때는 오늘이 아니라 교육 시작일(개강일)부터 만든다 — 학기 중간에 만들어도 지난 주 회차까지 있어야
        // 전체 회차(F 기준)가 학기 기준과 맞다. 지난 회차는 '예정'으로 남고 출결 확정 때 정리한다(ATT-16)
        scheduleService.generate(created.id(), new GenerateRequest(req.startDate(), req.endDate()), admin);
        return courseService.get(created.id(), admin); // 시간표가 들어간 최신 상태
    }
}
