package com.qrcheckin.assistant;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.qrcheckin.assistant.AssistantDtos.CoursesResult;
import com.qrcheckin.assistant.AssistantDtos.RequestOptionsResult;
import com.qrcheckin.assistant.AssistantDtos.SimulationResult;
import com.qrcheckin.assistant.AssistantDtos.SummaryResult;
import com.qrcheckin.assistant.AssistantDtos.UpcomingResult;
import com.qrcheckin.auth.AuthUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * AI 도우미 조회 도구를 LLM 없이 직접 호출해 보는 API(학생 본인 것만, 읽기 전용).
 * Swagger 에서 도구 결과를 확인하고, 테스트에서 숫자를 검증하는 데 쓴다.
 */
@Tag(name = "AI 도우미 도구", description = "LLM 이 쓰는 조회·계산 도구(학생 본인, 읽기 전용)")
@RestController
@RequestMapping("/api/v1/assistant/tools")
@PreAuthorize("hasRole('STUDENT')")
public class AssistantToolController {

    private final AssistantTools tools;

    public AssistantToolController(AssistantTools tools) {
        this.tools = tools;
    }

    @Operation(summary = "getMyCourses: 내 수강 수업(기본 현재 학기)")
    @GetMapping("/courses")
    public CoursesResult courses(@RequestParam(defaultValue = "false") boolean allSemesters,
                                 @AuthenticationPrincipal AuthUser user) {
        return tools.myCourses(user, allSemesters);
    }

    @Operation(summary = "getMyAttendanceSummary: 수업별 출결 요약·경고 단계")
    @GetMapping("/attendance-summary")
    public SummaryResult summary(@RequestParam(required = false) Long courseId,
                                 @RequestParam(defaultValue = "false") boolean allSemesters,
                                 @AuthenticationPrincipal AuthUser user) {
        return tools.attendanceSummary(user, courseId, allSemesters);
    }

    @Operation(summary = "getMyUpcomingSessions: 기간 안의 내 수업 회차(최대 31일)")
    @GetMapping("/sessions")
    public UpcomingResult sessions(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @AuthenticationPrincipal AuthUser user) {
        return tools.sessionsBetween(user, from, to);
    }

    @Operation(summary = "simulateMyAttendance: n번 더 지각·결석하면(기록은 바뀌지 않음)")
    @GetMapping("/simulate")
    public SimulationResult simulate(@RequestParam Long courseId,
                                     @RequestParam(defaultValue = "0") int addLate,
                                     @RequestParam(defaultValue = "0") int addAbsent,
                                     @AuthenticationPrincipal AuthUser user) {
        return tools.simulate(user, courseId, addLate, addAbsent);
    }

    @Operation(summary = "getMyRequestOptions: 출석 인정 신청 가능한 회차와 신청 화면 경로")
    @GetMapping("/request-options")
    public RequestOptionsResult requestOptions(@RequestParam Long courseId, @AuthenticationPrincipal AuthUser user) {
        return tools.requestOptions(user, courseId);
    }
}
