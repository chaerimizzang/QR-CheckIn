package com.qrcheckin.assistant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZoneId;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrcheckin.support.MutableClock;

/**
 * AI 도우미 조회·계산 도구: LLM 없이 숫자·권한·읽기 전용을 확인한다.
 * 오늘 = 2026-11-23(월) 13:00 KST.
 * 자료구조 회차 6개: 마감 3(결석·지각·지각) + 진행하지 않은 지난 회차 1 + 예정 2(11/24 화, 11/27 금)
 * → 전체 6, 허용 결석 ⌊6×0.25⌋=1, 인정 결석 = 1 + ⌊2/3⌋ = 1 → 남은 0(위험)
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:assistanttooltest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.checkin.close-check-ms=3600000",
        "app.push.check-ms=3600000",
        "app.attendance.warning-check-ms=3600000"
})
class AssistantToolsTest {

    static final Instant NOW = Instant.parse("2026-11-23T04:00:00Z"); // 13:00 KST
    static final String ROOM = "\"latitude\":37.5665,\"longitude\":126.978";
    static final String TOOLS = "/api/v1/assistant/tools";

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
    long absentSession;
    long tuesday;
    long friday;

    @BeforeEach
    void seed() throws Exception {
        n = String.valueOf(SEQ.incrementAndGet());
        prof = login("aprof" + n + "@t.com", "ADMIN");
        kim = login("akim" + n + "@t.com", "STUDENT");
        kimId = idOf(call(get("/api/v1/me"), kim, null));
        course = idOf(call(post("/api/v1/courses"), prof, "{\"title\":\"자료구조\"}"));
        call(post("/api/v1/courses/" + course + "/enrollments"), prof, "{\"email\":\"akim" + n + "@t.com\"}");
        // 화요일 13:00, 90분 수업
        call(put("/api/v1/courses/" + course + "/schedules"), prof,
                "{\"slots\":[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"13:00\",\"durationMinutes\":90}]}")
                .andExpect(status().isOk());

        absentSession = runSession("2026-11-03T13:00:00", null); // 표시 안 함 → 결석
        runSession("2026-11-10T13:00:00", "LATE");
        runSession("2026-11-17T13:00:00", "LATE");
        createSession("2026-11-20T13:00:00");             // 날짜가 지났는데 열지 않음 → 남은 회차 아님
        tuesday = createSession("2026-11-24T13:00:00");
        friday = createSession("2026-11-27T15:00:00");     // 시간표에 없는 시각 → 종료 시각 모름
    }

    @Test
    void 출결_요약은_출결_현황과_같은_숫자와_규칙을_준다() throws Exception {
        call(get(TOOLS + "/attendance-summary"), kim, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value("2026-11-23T13:00:00"))
                .andExpect(jsonPath("$.policy.latesPerAbsence").value(3))
                .andExpect(jsonPath("$.policy.failRatio").value(0.25))
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].title").value("자료구조"))
                .andExpect(jsonPath("$.courses[0].absentCount").value(1))
                .andExpect(jsonPath("$.courses[0].lateCount").value(2))
                .andExpect(jsonPath("$.courses[0].convertedAbsences").value(1))
                .andExpect(jsonPath("$.courses[0].remainingAbsences").value(0))
                .andExpect(jsonPath("$.courses[0].warningLevel").value("DANGER"))
                // 이름·학번·이메일은 LLM 에 보내지 않는다
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());

        call(get(TOOLS + "/courses"), kim, null)
                .andExpect(jsonPath("$.currentSemesterLabel").value("2026년 2학기"))
                .andExpect(jsonPath("$.courses[0].courseId").value(course))
                .andExpect(jsonPath("$.courses[0].currentSemester").value(true))
                .andExpect(jsonPath("$.courses[0].schedules[0].dayOfWeek").value("TUESDAY"));
    }

    @Test
    void 가정_계산은_같은_규칙으로_계산하고_기록을_바꾸지_않는다() throws Exception {
        // 지각 1 더 → 지각 3 = 결석 1 → 인정 2 > 허용 1 → F 기준
        call(get(TOOLS + "/simulate").param("courseId", String.valueOf(course)).param("addLate", "1"), kim, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseTitle").value("자료구조"))
                .andExpect(jsonPath("$.result.plannedSessions").value(6))
                .andExpect(jsonPath("$.result.remainingSessions").value(2))
                .andExpect(jsonPath("$.result.current.convertedAbsences").value(1))
                .andExpect(jsonPath("$.result.current.level").value("DANGER"))
                .andExpect(jsonPath("$.result.simulated.lateCount").value(3))
                .andExpect(jsonPath("$.result.simulated.convertedAbsences").value(2))
                .andExpect(jsonPath("$.result.simulated.remainingAbsences").value(-1))
                .andExpect(jsonPath("$.result.simulated.level").value("FAIL"))
                .andExpect(jsonPath("$.result.levelChanged").value(true))
                .andExpect(jsonPath("$.note").value(Matchers.containsString("가정")));

        // 남은 회차(2)보다 많이, 또는 0회는 가정할 수 없다
        call(get(TOOLS + "/simulate").param("courseId", String.valueOf(course)).param("addAbsent", "3"), kim, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("남은 수업이 2회라 그보다 많이 가정할 수 없어요"));
        call(get(TOOLS + "/simulate").param("courseId", String.valueOf(course)), kim, null)
                .andExpect(status().isBadRequest());

        // 기록은 그대로
        call(get(TOOLS + "/attendance-summary").param("courseId", String.valueOf(course)), kim, null)
                .andExpect(jsonPath("$.courses[0].lateCount").value(2))
                .andExpect(jsonPath("$.courses[0].convertedAbsences").value(1));
    }

    @Test
    void 기간_안의_수업과_종료_시각() throws Exception {
        call(get(TOOLS + "/sessions").param("from", "2026-11-23").param("to", "2026-11-29"), kim, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].sessionId").value(tuesday))
                .andExpect(jsonPath("$.sessions[0].startAt").value("2026-11-24T13:00:00"))
                .andExpect(jsonPath("$.sessions[0].endAt").value("2026-11-24T14:30:00"))
                .andExpect(jsonPath("$.sessions[1].sessionId").value(friday))
                .andExpect(jsonPath("$.sessions[1].endAt").doesNotExist());

        // 하루만
        call(get(TOOLS + "/sessions").param("from", "2026-11-24").param("to", "2026-11-24"), kim, null)
                .andExpect(jsonPath("$.sessions.length()").value(1));
        // 거꾸로 된 기간, 31일 넘는 기간, 날짜 형식 오류
        call(get(TOOLS + "/sessions").param("from", "2026-11-29").param("to", "2026-11-23"), kim, null)
                .andExpect(status().isBadRequest());
        call(get(TOOLS + "/sessions").param("from", "2026-11-01").param("to", "2026-12-15"), kim, null)
                .andExpect(status().isBadRequest());
        call(get(TOOLS + "/sessions").param("from", "다음주").param("to", "2026-11-29"), kim, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void 신청_가능_회차와_신청_화면_경로() throws Exception {
        call(get(TOOLS + "/request-options").param("courseId", String.valueOf(course)), kim, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalized").value(false))
                // 진행하지 않은 지난 회차는 목록에 없다(마감 3 + 예정 2)
                .andExpect(jsonPath("$.sessions.length()").value(5))
                .andExpect(jsonPath("$.sessions[?(@.sessionId == " + tuesday + ")].canRequest").value(true))
                .andExpect(jsonPath("$.sessions[?(@.sessionId == " + tuesday + ")].requestPath")
                        .value("/requests?new=1&course=" + course + "&session=" + tuesday));

        // 신청하면 처리 중 → 신청 불가로 바뀐다
        call(multipart("/api/v1/sessions/" + tuesday + "/requests").param("reason", "예비군 훈련"), kim, null)
                .andExpect(status().isOk());
        call(get(TOOLS + "/request-options").param("courseId", String.valueOf(course)), kim, null)
                .andExpect(jsonPath("$.sessions[?(@.sessionId == " + tuesday + ")].canRequest").value(false))
                .andExpect(jsonPath("$.sessions[?(@.sessionId == " + tuesday + ")].blockedReason")
                        .value("처리 중인 신청이 이미 있어요"));
    }

    @Test
    void 다른_학생과_교수는_볼_수_없다() throws Exception {
        String other = login("aother" + n + "@t.com", "STUDENT");
        String c = String.valueOf(course);

        call(get(TOOLS + "/attendance-summary").param("courseId", c), other, null).andExpect(status().isForbidden());
        call(get(TOOLS + "/simulate").param("courseId", c).param("addLate", "1"), other, null)
                .andExpect(status().isForbidden());
        call(get(TOOLS + "/request-options").param("courseId", c), other, null).andExpect(status().isForbidden());
        call(get(TOOLS + "/attendance-summary"), other, null).andExpect(jsonPath("$.courses.length()").value(0));
        call(get(TOOLS + "/sessions").param("from", "2026-11-23").param("to", "2026-11-29"), other, null)
                .andExpect(jsonPath("$.sessions.length()").value(0));
        // 없는 수업도 남의 수업과 똑같이 403(존재 여부를 알려 주지 않음)
        call(get(TOOLS + "/request-options").param("courseId", "999999"), kim, null).andExpect(status().isForbidden());

        // 교수 계정·로그인 안 함
        call(get(TOOLS + "/attendance-summary"), prof, null).andExpect(status().isForbidden());
        mvc.perform(get(TOOLS + "/attendance-summary")).andExpect(status().isUnauthorized());
    }

    // ---------------- 준비 ----------------

    private long createSession(String at) throws Exception {
        return idOf(call(post("/api/v1/courses/" + course + "/sessions"), prof,
                "{\"scheduledAt\":\"" + at + "\"," + ROOM + "}"));
    }

    private long runSession(String at, String mark) throws Exception {
        long s = createSession(at);
        call(post("/api/v1/sessions/" + s + "/open"), prof, null).andExpect(status().isOk());
        if (mark != null) {
            call(put("/api/v1/sessions/" + s + "/attendances/" + kimId), prof, "{\"status\":\"" + mark + "\"}")
                    .andExpect(status().isOk());
        }
        call(post("/api/v1/sessions/" + s + "/close"), prof, null).andExpect(status().isOk());
        return s;
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
