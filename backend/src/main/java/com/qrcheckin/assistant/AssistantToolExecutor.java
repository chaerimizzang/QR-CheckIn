package com.qrcheckin.assistant;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;

/**
 * LLM 에 알려 줄 도구 목록과, LLM 이 요청한 도구를 실제로 실행하는 부분.
 * 도구 인자에 학생 ID 는 없다 — 실행은 항상 로그인한 학생(AuthUser) 기준.
 */
@Component
class AssistantToolExecutor {

    /** OpenAI 호환 tools 정의(Gemini·Groq 공용) */
    private static final String DEFINITIONS = """
            [
              {"type":"function","function":{
                "name":"getMyCourses",
                "description":"내가 수강하는 수업 목록(courseId, 수업 이름, 학기, 주간 시간표). 수업 이름을 courseId 로 바꿀 때 먼저 쓴다.",
                "parameters":{"type":"object","properties":{
                  "allSemesters":{"type":"boolean","description":"지난 학기 수업까지 포함하려면 true. 기본 false(현재 학기)"}}}}},
              {"type":"function","function":{
                "name":"getMyAttendanceSummary",
                "description":"수업별 출석·지각·결석, 인정 결석(결석+지각/3), 출석률, F 기준까지 남은 결석, 경고 단계(NONE·SAFE·WARNING·DANGER·FAIL). courseId 를 빼면 현재 학기 전체.",
                "parameters":{"type":"object","properties":{
                  "courseId":{"type":"integer"},
                  "allSemesters":{"type":"boolean"}}}}},
              {"type":"function","function":{
                "name":"getMyUpcomingSessions",
                "description":"from~to(포함, 최대 31일) 사이 내 수업 회차와 시작·종료 시각, 휴강 여부(canceled). 예비군·병원처럼 특정 날짜에 겹치는 수업을 찾거나 '그날 수업 있어?'를 확인할 때 쓴다.",
                "parameters":{"type":"object","properties":{
                  "from":{"type":"string","description":"YYYY-MM-DD"},
                  "to":{"type":"string","description":"YYYY-MM-DD"}},
                  "required":["from","to"]}}},
              {"type":"function","function":{
                "name":"simulateMyAttendance",
                "description":"지각·결석이 더 생긴다고 가정했을 때의 인정 결석·남은 결석·경고 단계. 실제 기록은 바뀌지 않는다. 직접 계산하지 말고 이 도구를 쓴다.",
                "parameters":{"type":"object","properties":{
                  "courseId":{"type":"integer"},
                  "addLate":{"type":"integer","description":"추가 지각 횟수(0 이상)"},
                  "addAbsent":{"type":"integer","description":"추가 결석 횟수(0 이상)"}},
                  "required":["courseId"]}}},
              {"type":"function","function":{
                "name":"getMyRequestOptions",
                "description":"한 수업의 회차별 출석 인정 신청 가능 여부(미래 회차도 미리 신청 가능), 처리 중 신청, 내 출결 상태.",
                "parameters":{"type":"object","properties":{
                  "courseId":{"type":"integer"}},
                  "required":["courseId"]}}}
            ]
            """;

    private final AssistantTools tools;
    private final ObjectMapper om;
    private final ArrayNode definitions;

    AssistantToolExecutor(AssistantTools tools, ObjectMapper om) throws JsonProcessingException {
        this.tools = tools;
        this.om = om;
        this.definitions = (ArrayNode) om.readTree(DEFINITIONS);
    }

    /** 요청마다 새로 쓸 수 있게 복사본을 준다 */
    ArrayNode definitions() {
        return definitions.deepCopy();
    }

    /**
     * 도구 실행. 알 수 없는 도구·잘못된 인자는 IllegalArgumentException, 권한·검증 실패는 ApiException.
     * (둘 다 호출한 쪽에서 {"error": 메시지}로 바꿔 LLM 에 돌려준다)
     */
    Object execute(String name, String argumentsJson, AuthUser user) {
        JsonNode args = parseArgs(argumentsJson);
        return switch (name) {
            case "getMyCourses" -> tools.myCourses(user, args.path("allSemesters").asBoolean(false));
            case "getMyAttendanceSummary" -> tools.attendanceSummary(user, optionalLong(args, "courseId"),
                    args.path("allSemesters").asBoolean(false));
            case "getMyUpcomingSessions" -> tools.sessionsBetween(user, date(args, "from"), date(args, "to"));
            case "simulateMyAttendance" -> tools.simulate(user, requiredLong(args, "courseId"),
                    args.path("addLate").asInt(0), args.path("addAbsent").asInt(0));
            case "getMyRequestOptions" -> tools.requestOptions(user, requiredLong(args, "courseId"));
            default -> throw new IllegalArgumentException("없는 도구예요: " + name);
        };
    }

    private JsonNode parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return om.createObjectNode();
        }
        try {
            JsonNode node = om.readTree(json);
            return node.isObject() ? node : om.createObjectNode();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("도구 인자를 읽지 못했어요");
        }
    }

    private static Long optionalLong(JsonNode args, String field) {
        JsonNode v = args.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.canConvertToLong() && !v.isTextual()) {
            throw new IllegalArgumentException(field + " 는 숫자여야 해요");
        }
        try {
            return v.isTextual() ? Long.parseLong(v.asText().trim()) : v.asLong();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " 는 숫자여야 해요");
        }
    }

    private static Long requiredLong(JsonNode args, String field) {
        Long v = optionalLong(args, field);
        if (v == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "어느 수업인지 알려 주세요(" + field + ")");
        }
        return v;
    }

    private static LocalDate date(JsonNode args, String field) {
        String v = args.path(field).asText("");
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " 는 YYYY-MM-DD 날짜여야 해요");
        }
    }
}
