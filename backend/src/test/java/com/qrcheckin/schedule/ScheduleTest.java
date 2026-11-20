package com.qrcheckin.schedule;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZoneId;

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

/** 교육 주간 시간표 + 시간표로 회차 자동 생성 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:scheduletest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.checkin.close-check-ms=3600000"
})
class ScheduleTest {

    /** 2026-11-18(수) 13:00 KST */
    static final Instant NOW = Instant.parse("2026-11-18T04:00:00Z");
    static final String WEEKLY = "{\"slots\":["
            + "{\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"13:00\",\"durationMinutes\":60},"
            + "{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"durationMinutes\":120}]}";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW, ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void 시간표를_저장하고_보여준다() throws Exception {
        String prof = login("sprof@t.com", "ADMIN");
        String other = login("sother@t.com", "ADMIN");
        String kim = login("skim@t.com", "STUDENT");
        long c = idOf(call(post("/api/v1/courses"), prof,
                "{\"title\":\"데이터베이스\",\"startDate\":\"2026-11-16\",\"endDate\":\"2026-11-30\"}"));
        call(post("/api/v1/courses/" + c + "/enrollments"), prof, "{\"email\":\"skim@t.com\"}");

        // 저장하면 월요일부터 정렬돼서 돌아온다
        call(put("/api/v1/courses/" + c + "/schedules"), prof, WEEKLY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$[0].startTime").value("09:00"))
                .andExpect(jsonPath("$[0].durationMinutes").value(120))
                .andExpect(jsonPath("$[1].dayOfWeek").value("WEDNESDAY"));

        // 교육 정보에도 같이 나온다(목록·학생 화면에서 사용)
        call(get("/api/v1/courses/" + c), kim, null)
                .andExpect(jsonPath("$.schedules.length()").value(2))
                .andExpect(jsonPath("$.schedules[1].startTime").value("13:00"));
        call(get("/api/v1/courses/" + c + "/schedules"), kim, null).andExpect(status().isOk());

        // 같은 요일 겹침, 자정 넘김, 잘못된 길이는 거절
        call(put("/api/v1/courses/" + c + "/schedules"), prof, "{\"slots\":["
                + "{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"durationMinutes\":120},"
                + "{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"10:00\",\"durationMinutes\":60}]}")
                .andExpect(status().isBadRequest());
        call(put("/api/v1/courses/" + c + "/schedules"), prof,
                "{\"slots\":[{\"dayOfWeek\":\"FRIDAY\",\"startTime\":\"23:00\",\"durationMinutes\":120}]}")
                .andExpect(status().isBadRequest());
        call(put("/api/v1/courses/" + c + "/schedules"), prof,
                "{\"slots\":[{\"dayOfWeek\":\"FRIDAY\",\"startTime\":\"09:00\",\"durationMinutes\":5}]}")
                .andExpect(status().isBadRequest());
        // 거절된 뒤에도 기존 시간표는 그대로
        call(get("/api/v1/courses/" + c + "/schedules"), prof, null).andExpect(jsonPath("$.length()").value(2));

        // 남의 교육·수강생은 바꿀 수 없다
        call(put("/api/v1/courses/" + c + "/schedules"), other, WEEKLY).andExpect(status().isForbidden());
        call(put("/api/v1/courses/" + c + "/schedules"), kim, WEEKLY).andExpect(status().isForbidden());
        call(get("/api/v1/courses/" + c + "/schedules"), other, null).andExpect(status().isForbidden());

        // 시간표가 있어도 교육 삭제 가능
        call(delete("/api/v1/courses/" + c), prof, null).andExpect(status().is2xxSuccessful());
    }

    @Test
    void 시간표로_회차를_만든다_중복없이() throws Exception {
        String prof = login("gprof@t.com", "ADMIN");
        long c = idOf(call(post("/api/v1/courses"), prof,
                "{\"title\":\"운영체제\",\"startDate\":\"2026-11-16\",\"endDate\":\"2026-11-30\"}"));

        // 시간표 없이 만들면 안내
        call(post("/api/v1/courses/" + c + "/sessions/generate"), prof, null).andExpect(status().isBadRequest());

        call(put("/api/v1/courses/" + c + "/schedules"), prof, WEEKLY).andExpect(status().isOk());
        // 오늘(수) 13:00 회차는 직접 만들어 둔 상태 + 장소 지정
        call(post("/api/v1/courses/" + c + "/sessions"), prof,
                "{\"scheduledAt\":\"2026-11-18T13:00:00\",\"latitude\":37.5665,\"longitude\":126.978,\"radiusM\":80}");

        // 기본 기간: 오늘(11/18) ~ 교육 종료일(11/30)
        // 11/18 수 13:00(이미 있음) · 11/23 월 09:00 · 11/25 수 13:00 · 11/30 월 09:00
        call(post("/api/v1/courses/" + c + "/sessions/generate"), prof, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.from").value("2026-11-18"))
                .andExpect(jsonPath("$.to").value("2026-11-30"));

        call(get("/api/v1/courses/" + c + "/sessions"), prof, null)
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[1].sessionNo").value(2))
                .andExpect(jsonPath("$[1].scheduledAt").value("2026-11-23T09:00:00"))
                // 장소·반경은 장소를 정해 둔 회차를 따라간다
                .andExpect(jsonPath("$[1].latitude").value(37.5665))
                .andExpect(jsonPath("$[1].radiusM").value(80))
                .andExpect(jsonPath("$[3].scheduledAt").value("2026-11-30T09:00:00"));

        // 다시 눌러도 중복으로 생기지 않는다
        call(post("/api/v1/courses/" + c + "/sessions/generate"), prof, null)
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skipped").value(4));

        // 기간 직접 지정 + 잘못된 기간
        call(post("/api/v1/courses/" + c + "/sessions/generate"), prof, "{\"from\":\"2026-12-01\",\"to\":\"2026-12-02\"}")
                .andExpect(jsonPath("$.created").value(1)); // 12/2 수
        call(post("/api/v1/courses/" + c + "/sessions/generate"), prof, "{\"from\":\"2026-12-10\",\"to\":\"2026-12-01\"}")
                .andExpect(status().isBadRequest());
    }

    // ---- 도우미 ----

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
