package com.qrcheckin.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrcheckin.support.MutableClock;

/**
 * 휴강: 오늘 = 2026-11-23(월) 13:00 KST.
 * 회차 8개 = 마감 1(결석) + 진행하지 않은 지난 회차 1(11/20) + 예정 6(11/24~12/2)
 * → 전체 8, 허용 결석 ⌊8/4⌋=2, 인정 결석 1 → 남은 1(주의).
 * 11/24 를 휴강하면 전체 7, 허용 1 → 남은 0(위험).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sessioncanceltest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.checkin.close-check-ms=3600000",
        "app.push.check-ms=3600000",
        "app.attendance.warning-check-ms=3600000"
})
class SessionCancelTest {

    static final Instant NOW = Instant.parse("2026-11-23T04:00:00Z");
    static final String ROOM = "\"latitude\":37.5665,\"longitude\":126.978";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW, ZoneId.of("Asia/Seoul"));
        }
    }

    static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    String n;
    String prof;
    String kim;
    long kimId;
    long course;
    long closed;
    long nov24;
    long nov25;

    @BeforeEach
    void seed() throws Exception {
        n = String.valueOf(SEQ.incrementAndGet());
        prof = login("cprof" + n + "@t.com", "ADMIN");
        kim = login("ckim" + n + "@t.com", "STUDENT");
        kimId = idOf(call(get("/api/v1/me"), kim, null));
        course = idOf(call(post("/api/v1/courses"), prof, "{\"title\":\"자료구조\"}"));
        call(post("/api/v1/courses/" + course + "/enrollments"), prof, "{\"email\":\"ckim" + n + "@t.com\"}");

        closed = createSession("2026-11-03T13:00:00");
        call(post("/api/v1/sessions/" + closed + "/open"), prof, null).andExpect(status().isOk());
        call(post("/api/v1/sessions/" + closed + "/close"), prof, null).andExpect(status().isOk()); // 결석
        createSession("2026-11-20T13:00:00"); // 진행하지 않은 지난 회차
        nov24 = createSession("2026-11-24T13:00:00");
        nov25 = createSession("2026-11-25T13:00:00");
        createSession("2026-11-26T13:00:00");
        createSession("2026-11-27T13:00:00");
        createSession("2026-12-01T13:00:00");
        createSession("2026-12-02T13:00:00");
    }

    @Test
    void 휴강하면_전체_회차에서_빠져_F_기준이_다시_계산된다() throws Exception {
        summary().andExpect(jsonPath("$.courses[0].remainingAbsences").value(1))
                .andExpect(jsonPath("$.courses[0].warningLevel").value("WARNING"));
        rate().andExpect(jsonPath("$.plannedSessions").value(8)).andExpect(jsonPath("$.allowedAbsences").value(2));

        call(post("/api/v1/sessions/" + nov24 + "/cancel"), prof, "{\"reason\":\"학회 참석\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelReason").value("학회 참석"));

        summary().andExpect(jsonPath("$.courses[0].remainingAbsences").value(0))
                .andExpect(jsonPath("$.courses[0].warningLevel").value("DANGER"));
        rate().andExpect(jsonPath("$.plannedSessions").value(7)).andExpect(jsonPath("$.allowedAbsences").value(1));
        // 가정 계산의 전체·남은 회차에서도 빠진다(남은 = 예정 6 − 휴강 1)
        call(get("/api/v1/assistant/tools/simulate").param("courseId", String.valueOf(course)).param("addAbsent", "1"), kim, null)
                .andExpect(jsonPath("$.result.plannedSessions").value(7))
                .andExpect(jsonPath("$.result.remainingSessions").value(5));

        // 휴강 취소하면 원래대로
        call(post("/api/v1/sessions/" + nov24 + "/uncancel"), prof, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.cancelReason").doesNotExist());
        rate().andExpect(jsonPath("$.plannedSessions").value(8));
    }

    @Test
    void 휴강_회차는_출석_수정_신청을_받지_않는다() throws Exception {
        call(put("/api/v1/sessions/" + nov24), prof, "{\"scheduledAt\":\"2026-11-24T13:00:00\"," + ROOM + "}")
                .andExpect(status().isOk()); // 장소 지정
        call(post("/api/v1/sessions/" + nov24 + "/cancel"), prof, null).andExpect(status().isOk()); // 사유 없이

        call(post("/api/v1/sessions/" + nov24 + "/open"), prof, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_CANCELED"));
        call(put("/api/v1/sessions/" + nov24), prof, "{\"scheduledAt\":\"2026-11-24T14:00:00\"," + ROOM + "}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_CANCELED"));
        call(put("/api/v1/sessions/" + nov24 + "/attendances/" + kimId), prof, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isConflict());

        // 신청 목록에서 빠지고, 직접 신청해도 거절
        call(get("/api/v1/courses/" + course + "/request-options"), kim, null)
                .andExpect(jsonPath("$[?(@.sessionId == " + nov24 + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.sessionId == " + nov25 + ")]").isNotEmpty());
        call(multipart("/api/v1/sessions/" + nov24 + "/requests").param("reason", "예비군"), kim, null)
                .andExpect(status().isConflict());

        // 학생 내 출결과 AI 기간 조회에 휴강으로 보인다
        call(get("/api/v1/me/attendances").param("courseId", String.valueOf(course)), kim, null)
                .andExpect(jsonPath("$.sessions[?(@.sessionId == " + nov24 + ")].sessionStatus").value("CANCELED"));
        call(get("/api/v1/assistant/tools/sessions").param("from", "2026-11-24").param("to", "2026-11-24"), kim, null)
                .andExpect(jsonPath("$.sessions[0].canceled").value(true));

        // 휴강 회차는 지울 수 있다
        call(delete("/api/v1/sessions/" + nov24), prof, null).andExpect(status().isNoContent());
    }

    @Test
    void 처리_대기_신청은_휴강하면_취소된다() throws Exception {
        call(multipart("/api/v1/sessions/" + nov25 + "/requests").param("reason", "병원"), kim, null)
                .andExpect(status().isOk());
        call(post("/api/v1/sessions/" + nov25 + "/cancel"), prof, "{\"reason\":\"\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelReason").doesNotExist()); // 빈 사유는 없음으로
        call(get("/api/v1/me/requests"), kim, null)
                .andExpect(jsonPath("$[0].status").value("CANCELED"));
    }

    @Test
    void 시작한_회차는_휴강할_수_없고_남의_과목은_안_된다() throws Exception {
        call(post("/api/v1/sessions/" + closed + "/cancel"), prof, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_NOT_EDITABLE"));
        call(post("/api/v1/sessions/" + nov24 + "/uncancel"), prof, null).andExpect(status().isBadRequest());

        String other = login("cother" + n + "@t.com", "ADMIN");
        call(post("/api/v1/sessions/" + nov24 + "/cancel"), other, null).andExpect(status().isForbidden());
        call(post("/api/v1/sessions/" + nov24 + "/cancel"), kim, null).andExpect(status().isForbidden());
        call(post("/api/v1/sessions/" + nov24 + "/cancel"), prof, "{\"reason\":\"" + "가".repeat(201) + "\"}")
                .andExpect(status().isBadRequest());
    }

    // ---------------- 준비 ----------------

    private ResultActions summary() throws Exception {
        return call(get("/api/v1/assistant/tools/attendance-summary"), kim, null);
    }

    private ResultActions rate() throws Exception {
        return call(get("/api/v1/courses/" + course + "/attendance/rate"), prof, null);
    }

    private long createSession(String at) throws Exception {
        return idOf(call(post("/api/v1/courses/" + course + "/sessions"), prof,
                "{\"scheduledAt\":\"" + at + "\"," + ROOM + "}"));
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
