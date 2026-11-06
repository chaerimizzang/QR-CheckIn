package com.qrcheckin.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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
import com.qrcheckin.checkin.SessionRunService;
import com.qrcheckin.support.MutableClock;

/** 시나리오 D: 마감 → 결석 자동 처리 → 관리자가 지각으로 수정, 스케줄러 재실행 후에도 유지 (B-10 ~ B-12) */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:attendancetest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.checkin.close-check-ms=3600000"
})
class AttendanceManageTest {

    static final Instant START = Instant.parse("2026-11-10T04:00:00Z"); // 13:00 KST

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START, ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired SessionRunService sessionRunService;

    @Test
    void 마감_후_지각_처리_시나리오() throws Exception {
        String prof = login("prof@t.com", "ADMIN");
        String other = login("other@t.com", "ADMIN");
        String kim = login("kim@t.com", "STUDENT");   // 정상 출석
        String lee = login("lee@t.com", "STUDENT");   // 늦게 와서 지각 처리
        long leeId = myId(lee);
        long kimId = myId(kim);

        long courseId = idOf(call(post("/api/v1/courses"), prof, "{\"title\":\"데이터베이스 실습\"}"));
        call(post("/api/v1/courses/" + courseId + "/enrollments"), prof, "{\"email\":\"kim@t.com\"}");
        call(post("/api/v1/courses/" + courseId + "/enrollments"), prof, "{\"email\":\"lee@t.com\"}");
        long s1 = idOf(call(post("/api/v1/courses/" + courseId + "/sessions"), prof,
                "{\"scheduledAt\":\"2026-11-10T13:00:00\",\"latitude\":37.5665,\"longitude\":126.978}"));
        long s2 = idOf(call(post("/api/v1/courses/" + courseId + "/sessions"), prof,
                "{\"scheduledAt\":\"2026-11-17T13:00:00\"}"));

        call(post("/api/v1/sessions/" + s1 + "/open"), prof, null).andExpect(status().isOk());

        // 진행 중 현황: 아무도 기록 없음
        call(get("/api/v1/sessions/" + s1 + "/attendances"), prof, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.enrolled").value(2))
                .andExpect(jsonPath("$.summary.pending").value(2));

        // kim 정상 출석
        clock.advance(Duration.ofSeconds(10));
        String token = om.readTree(call(get("/api/v1/sessions/" + s1 + "/qr"), prof, null)
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        call(post("/api/v1/check-in"), kim, "{\"token\":\"" + token
                + "\",\"latitude\":37.5665,\"longitude\":126.978,\"accuracy\":15}")
                .andExpect(status().isOk());

        // 13:05 마감 → lee 자동 결석
        clock.set(START.plusSeconds(5 * 60));
        sessionRunService.closeExpiredSessions();
        call(get("/api/v1/sessions/" + s1 + "/attendances"), prof, null)
                .andExpect(jsonPath("$.sessionStatus").value("CLOSED"))
                .andExpect(jsonPath("$.summary.present").value(1))
                .andExpect(jsonPath("$.summary.absent").value(1))
                .andExpect(jsonPath("$.summary.pending").value(0));

        // 13:12 lee 도착 → 교수가 휴대폰에서 결석 → 지각 (위치 검증 없음)
        clock.set(START.plusSeconds(12 * 60));
        call(put("/api/v1/sessions/" + s1 + "/attendances/" + leeId), prof, "{\"status\":\"LATE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LATE"))
                .andExpect(jsonPath("$.source").value("MANUAL"));

        // 스케줄러를 다시 돌려도 LATE 유지
        sessionRunService.closeExpiredSessions();
        call(get("/api/v1/sessions/" + s1 + "/attendances"), prof, null)
                .andExpect(jsonPath("$.summary.late").value(1))
                .andExpect(jsonPath("$.summary.absent").value(0));

        // 권한: 학생·다른 관리자는 수정 불가, 수강생이 아닌 사용자는 404, 잘못된 값은 400
        call(put("/api/v1/sessions/" + s1 + "/attendances/" + leeId), lee, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isForbidden());
        call(put("/api/v1/sessions/" + s1 + "/attendances/" + leeId), other, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isForbidden());
        call(put("/api/v1/sessions/" + s1 + "/attendances/" + myId(other)), prof, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isNotFound());
        call(put("/api/v1/sessions/" + s1 + "/attendances/" + leeId), prof, "{\"status\":\"SLEEPING\"}")
                .andExpect(status().isBadRequest());

        // 아직 시작 안 한 2회차도 미리 출석 처리 가능(기록이 없던 학생 → 새로 생성)
        call(put("/api/v1/sessions/" + s2 + "/attendances/" + kimId), prof, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.attendanceId").exists());

        // B-10 lee 의 내 출결: 1회차 LATE, 2회차 아직 없음
        call(get("/api/v1/me/attendances?courseId=" + courseId), lee, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseTitle").value("데이터베이스 실습"))
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].status").value("LATE"))
                .andExpect(jsonPath("$.sessions[1].status").value(nullValue()));
        // 관리자는 이 API 를 쓰지 않음, courseId 누락은 400
        call(get("/api/v1/me/attendances?courseId=" + courseId), prof, null).andExpect(status().isForbidden());
        call(get("/api/v1/me/attendances"), lee, null).andExpect(status().isBadRequest());

        // 수강하지 않는 교육은 403
        long otherCourse = idOf(call(post("/api/v1/courses"), other, "{\"title\":\"남의 수업\"}"));
        call(get("/api/v1/me/attendances?courseId=" + otherCourse), lee, null).andExpect(status().isForbidden());

        assertThat(leeId).isNotEqualTo(kimId);
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

    private long myId(String token) throws Exception {
        return idOf(call(get("/api/v1/me"), token, null));
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
