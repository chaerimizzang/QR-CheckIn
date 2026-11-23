package com.qrcheckin.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qrcheckin.assistant.llm.LlmClient;
import com.qrcheckin.assistant.llm.LlmException;
import com.qrcheckin.support.MutableClock;

/**
 * AI 도우미 대화: 실제 LLM 대신 정해진 답을 돌려주는 가짜 LLM 으로
 * 도구 호출 반복·근거 카드·숫자 대조·템플릿 대체·권한·호출 제한을 확인한다.
 * 오늘 = 2026-11-23(월) 13:00 KST. 자료구조: 결석 1·지각 2(인정 결석 1), 전체 6회, 허용 1 → 위험
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:assistantservicetest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.checkin.close-check-ms=3600000",
        "app.push.check-ms=3600000",
        "app.attendance.warning-check-ms=3600000",
        "app.assistant.startup-check=false",
        "app.assistant.preset-direct=false",
        "app.assistant.max-tool-calls=6",
        "app.assistant.per-minute-limit=5",
        "app.assistant.daily-limit=100"
})
class AssistantServiceTest {

    static final Instant NOW = Instant.parse("2026-11-23T04:00:00Z");
    static final String ROOM = "\"latitude\":37.5665,\"longitude\":126.978";
    static final String ASK = "/api/v1/assistant/messages";

    /** 정해 둔 응답을 순서대로 돌려주는 가짜 LLM */
    static class FakeLlm implements LlmClient {
        final Deque<Object> script = new ArrayDeque<>();
        final List<ArrayNode> requests = new ArrayList<>();
        final List<Boolean> withTools = new ArrayList<>();
        boolean configured = true;

        void reset() {
            script.clear();
            requests.clear();
            withTools.clear();
            configured = true;
        }

        @Override
        public boolean configured() {
            return configured;
        }

        @Override
        public String model() {
            return "fake-model";
        }

        @Override
        public JsonNode chat(ArrayNode messages, ArrayNode tools) throws LlmException {
            requests.add(messages.deepCopy());
            withTools.add(tools != null && !tools.isEmpty());
            Object next = script.poll();
            if (next instanceof LlmException e) {
                throw e;
            }
            if (next == null) {
                throw new LlmException(LlmException.Kind.FAILED, "준비한 응답이 없어요");
            }
            return (JsonNode) next;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW, ZoneId.of("Asia/Seoul"));
        }

        @Bean
        @Primary
        FakeLlm fakeLlm() {
            return new FakeLlm();
        }
    }

    static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired FakeLlm fake;

    String n;
    String prof;
    String kim;
    long kimId;
    long course;
    long tuesday;
    long friday;

    @BeforeEach
    void seed() throws Exception {
        fake.reset();
        n = String.valueOf(SEQ.incrementAndGet());
        prof = login("sprof" + n + "@t.com", "ADMIN");
        kim = login("skim" + n + "@t.com", "STUDENT");
        kimId = idOf(call(get("/api/v1/me"), kim, null));
        course = idOf(call(post("/api/v1/courses"), prof, "{\"title\":\"자료구조\"}"));
        call(post("/api/v1/courses/" + course + "/enrollments"), prof, "{\"email\":\"skim" + n + "@t.com\"}");
        call(put("/api/v1/courses/" + course + "/schedules"), prof,
                "{\"slots\":[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"13:00\",\"durationMinutes\":90}]}");
        runSession("2026-11-03T13:00:00", null);
        runSession("2026-11-10T13:00:00", "LATE");
        runSession("2026-11-17T13:00:00", "LATE");
        createSession("2026-11-20T13:00:00");
        tuesday = createSession("2026-11-24T13:00:00");
        friday = createSession("2026-11-27T15:00:00");
    }

    @Test
    void 도구를_실행해_근거와_함께_답한다() throws Exception {
        fake.script.add(toolCall("c1", "getMyAttendanceSummary", "{}"));
        fake.script.add(text("**자료구조**는 인정 결석 1회로 위험 단계예요. 1번만 더 결석해도 F 기준을 넘어요."));

        ask("{\"message\":\"출결 위험한 수업 있어?\",\"history\":[{\"role\":\"user\",\"text\":\"안녕\"},"
                + "{\"role\":\"assistant\",\"text\":\"안녕하세요\"}]}", kim)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.answer").value("자료구조는 인정 결석 1회로 위험 단계예요. 1번만 더 결석해도 F 기준을 넘어요."))
                .andExpect(jsonPath("$.needsClarification").value(false))
                .andExpect(jsonPath("$.facts[0].label").value("자료구조"))
                .andExpect(jsonPath("$.facts[0].value").value(Matchers.containsString("위험")))
                .andExpect(jsonPath("$.actions[0].path").value("/student/courses/" + course + "?from=attendance"))
                .andExpect(jsonPath("$.policy.latesPerAbsence").value(3))
                .andExpect(jsonPath("$.asOf").value("2026-11-23T13:00:00"));

        // 첫 요청: 시스템 지시(오늘 날짜) + 이전 대화 + 질문, 도구 목록 포함
        ArrayNode first = fake.requests.get(0);
        assertThat(first.get(0).path("content").asText()).contains("2026-11-23(월)");
        assertThat(first.get(1).path("content").asText()).isEqualTo("안녕");
        assertThat(first.get(3).path("content").asText()).isEqualTo("출결 위험한 수업 있어?");
        assertThat(fake.withTools.get(0)).isTrue();
        // 두 번째 요청: 받은 도구 호출(thought signature 포함)을 그대로 돌려주고, 결과를 tool 메시지로 붙인다
        ArrayNode second = fake.requests.get(1);
        JsonNode echo = second.get(second.size() - 2);
        assertThat(echo.path("tool_calls").get(0).path("extra_content").path("google").path("thought_signature").asText())
                .isEqualTo("sig-c1");
        JsonNode toolMsg = second.get(second.size() - 1);
        assertThat(toolMsg.path("role").asText()).isEqualTo("tool");
        assertThat(toolMsg.path("tool_call_id").asText()).isEqualTo("c1");
        assertThat(toolMsg.path("content").asText()).contains("\"convertedAbsences\":1").doesNotContain("skim");
    }

    @Test
    void 도구_결과에_없는_숫자는_다시_요청하고_그래도_틀리면_안전한_문장() throws Exception {
        fake.script.add(toolCall("c1", "getMyAttendanceSummary", "{}"));
        fake.script.add(text("자료구조 결석은 77회예요."));
        fake.script.add(text("자료구조 결석은 88회예요."));
        ask("{\"message\":\"자료구조 결석 몇 번이야?\"}", kim)
                .andExpect(jsonPath("$.answer").value(AssistantService.SAFE_FALLBACK))
                .andExpect(jsonPath("$.facts[0].label").value("자료구조"));
        // 다시 요청할 때도 도구를 쓸 수 있다(짐작 대신 확인하도록)
        assertThat(fake.withTools.get(2)).isTrue();

        // 다시 요청해서 맞게 고치면 고친 답을 쓴다
        fake.reset();
        fake.script.add(toolCall("c1", "getMyAttendanceSummary", "{}"));
        fake.script.add(text("자료구조 결석은 77회예요."));
        fake.script.add(text("자료구조 결석은 1회, 지각은 2회예요."));
        ask("{\"message\":\"자료구조 결석 몇 번이야?\"}", kim)
                .andExpect(jsonPath("$.answer").value("자료구조 결석은 1회, 지각은 2회예요."));
    }

    @Test
    void 날짜는_숫자_대조에서_빼고_도구_없이_지어낸_숫자는_근거_없음_문장() throws Exception {
        fake.script.add(text("네, 11월 24일(화) 13:00에 자료구조 수업이 있어요."));
        ask("{\"message\":\"내일 수업 있어?\"}", kim)
                .andExpect(jsonPath("$.answer").value("네, 11월 24일(화) 13:00에 자료구조 수업이 있어요."));

        fake.reset();
        fake.script.add(text("이번 주 수업은 33개예요."));
        fake.script.add(text("이번 주 수업은 44개예요."));
        ask("{\"message\":\"이번 주 수업 몇 개야?\"}", kim)
                .andExpect(jsonPath("$.answer").value(AssistantService.NO_FACTS_FALLBACK))
                .andExpect(jsonPath("$.actions[0].path").value("/attendance"));
        // 고쳐 달라는 요청에 짐작하지 말고 도구로 확인하라는 말이 들어간다
        ArrayNode retry = fake.requests.get(1);
        assertThat(last(retry).path("content").asText()).contains("도구로 확인");
    }

    @Test
    void LLM이_남의_수업을_요청해도_오류만_돌려준다() throws Exception {
        long other = idOf(call(post("/api/v1/courses"), prof, "{\"title\":\"운영체제\"}"));
        fake.script.add(toolCall("c1", "simulateMyAttendance", "{\"courseId\":" + other + ",\"addLate\":1}"));
        fake.script.add(text("그 수업은 수강하지 않아서 볼 수 없어요."));

        ask("{\"message\":\"운영체제에서 지각 한 번 더 하면?\"}", kim).andExpect(status().isOk());
        JsonNode toolMsg = last(fake.requests.get(1));
        assertThat(toolMsg.path("content").asText()).contains("error").doesNotContain("plannedSessions");
    }

    @Test
    void 도구_호출이_계속되면_한도에서_멈추고_답을_받는다() throws Exception {
        for (int i = 0; i < 6; i++) {
            fake.script.add(toolCall("c" + i, "getMyCourses", "{}"));
        }
        fake.script.add(text("수업 목록을 확인했어요."));
        ask("{\"message\":\"내 수업 알려 줘\"}", kim)
                .andExpect(jsonPath("$.answer").value("수업 목록을 확인했어요."));
        assertThat(fake.withTools.subList(0, 6)).containsOnly(true);
        assertThat(fake.withTools.get(6)).isFalse();
    }

    @Test
    void LLM이_실패하면_추천_질문은_템플릿으로_답한다() throws Exception {
        fake.script.add(new LlmException(LlmException.Kind.RATE_LIMITED, "429"));
        ask("{\"message\":\"이번 학기에 출결이 위험한 수업이 있어?\",\"preset\":\"RISK\"}", kim)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.answer").value(Matchers.containsString("자료구조(위험, 남은 결석 0회)")))
                .andExpect(jsonPath("$.actions[0].path").value("/student/courses/" + course + "?from=attendance"));

        // 추천 질문이 아니면 503 — 분당 한도는 몇 초 뒤에 다시, 하루 한도는 오늘 사용량을 다 썼다고 구분
        fake.script.add(new LlmException(LlmException.Kind.RATE_LIMITED, "429", null, false, 23));
        ask("{\"message\":\"아무 질문\"}", kim)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("23초쯤 뒤에 다시")));
        fake.script.add(new LlmException(LlmException.Kind.RATE_LIMITED, "429", null, true, 0));
        ask("{\"message\":\"아무 질문\"}", kim)
                .andExpect(jsonPath("$.message").value(Matchers.containsString("오늘 AI 무료 사용량")));
    }

    @Test
    void LLM_설정이_없어도_추천_질문은_답한다() throws Exception {
        fake.configured = false;
        call(get("/api/v1/assistant/status"), kim, null)
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.presets.length()").value(3));

        ask("{\"message\":\"한 번 더 결석하면?\",\"preset\":\"ONE_MORE_ABSENCE\"}", kim)
                .andExpect(jsonPath("$.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.answer").value(Matchers.containsString("인정 결석이 1회에서 2회가 되고")))
                .andExpect(jsonPath("$.answer").value(Matchers.containsString("위험에서 F 기준 초과(으)로")));

        ask("{\"message\":\"이번 주 신청\",\"preset\":\"THIS_WEEK_REQUESTS\"}", kim)
                .andExpect(jsonPath("$.answer").value(Matchers.containsString("신청할 수 있는 회차는 2개예요")))
                .andExpect(jsonPath("$.actions[?(@.path == '/requests?new=1&course=" + course + "&session=" + tuesday
                        + "')]").exists())
                .andExpect(jsonPath("$.actions[?(@.path == '/requests?new=1&course=" + course + "&session=" + friday
                        + "')]").exists());

        ask("{\"message\":\"아무 질문\"}", kim).andExpect(status().isServiceUnavailable());
        assertThat(fake.requests).isEmpty();
    }

    @Test
    void 학생별_분당_호출_제한() throws Exception {
        fake.configured = false;
        for (int i = 0; i < 5; i++) {
            ask("{\"message\":\"위험한 수업?\",\"preset\":\"RISK\"}", kim).andExpect(status().isOk());
        }
        ask("{\"message\":\"위험한 수업?\",\"preset\":\"RISK\"}", kim).andExpect(status().isTooManyRequests());
    }

    @Test
    void 교수는_쓸_수_없고_빈_질문은_거절() throws Exception {
        ask("{\"message\":\"출결 알려 줘\"}", prof).andExpect(status().isForbidden());
        ask("{\"message\":\"  \"}", kim).andExpect(status().isBadRequest());
        ask("{\"message\":\"" + "가".repeat(301) + "\"}", kim).andExpect(status().isBadRequest());
        assertThat(fake.requests).isEmpty();
    }

    // ---------------- 가짜 LLM 응답 ----------------

    private ObjectNode text(String content) {
        ObjectNode m = om.createObjectNode();
        m.put("role", "assistant");
        m.put("content", content);
        return m;
    }

    private ObjectNode toolCall(String id, String name, String args) {
        ObjectNode m = om.createObjectNode();
        m.put("role", "assistant");
        m.putNull("content");
        ObjectNode call = m.putArray("tool_calls").addObject();
        call.put("id", id);
        call.put("type", "function");
        call.putObject("function").put("name", name).put("arguments", args);
        call.putObject("extra_content").putObject("google").put("thought_signature", "sig-" + id);
        return m;
    }

    private static JsonNode last(ArrayNode a) {
        return a.get(a.size() - 1);
    }

    // ---------------- 준비 ----------------

    private ResultActions ask(String body, String token) throws Exception {
        return call(post(ASK), token, body);
    }

    private long createSession(String at) throws Exception {
        return idOf(call(post("/api/v1/courses/" + course + "/sessions"), prof,
                "{\"scheduledAt\":\"" + at + "\"," + ROOM + "}"));
    }

    private void runSession(String at, String mark) throws Exception {
        long s = createSession(at);
        call(post("/api/v1/sessions/" + s + "/open"), prof, null).andExpect(status().isOk());
        if (mark != null) {
            call(put("/api/v1/sessions/" + s + "/attendances/" + kimId), prof, "{\"status\":\"" + mark + "\"}")
                    .andExpect(status().isOk());
        }
        call(post("/api/v1/sessions/" + s + "/close"), prof, null).andExpect(status().isOk());
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private long idOf(ResultActions result) throws Exception {
        return om.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String login(String email, String role) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"" + email
                        + "\",\"role\":\"" + role + "\"}"));
        String res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asText();
    }
}
