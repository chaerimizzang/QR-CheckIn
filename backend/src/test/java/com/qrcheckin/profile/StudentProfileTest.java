package com.qrcheckin.profile;

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

/** 학생 프로필: 내 정보 + 수강 중인 교육별 출석률(지각 3회 = 결석 1회, 마감된 회차만) */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:profiletest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.checkin.close-check-ms=3600000"
})
class StudentProfileTest {

    static final Instant START = Instant.parse("2026-11-10T04:00:00Z"); // 13:00 KST
    static final String ROOM = "\"latitude\":37.5665,\"longitude\":126.978";

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

    @Test
    void 내_프로필에_수강_교육별_출석률이_나온다() throws Exception {
        String prof = login("pprof@t.com", "ADMIN", "김교수");
        String other = login("pother@t.com", "ADMIN", "박교수");
        String kim = login("pkim@t.com", "STUDENT", "김학생");
        String lee = login("plee@t.com", "STUDENT", "이학생");

        long db = idOf(call(post("/api/v1/courses"), prof, "{\"title\":\"데이터베이스\"}"));
        enroll(prof, db, "pkim@t.com");
        enroll(prof, db, "plee@t.com");
        long se = idOf(call(post("/api/v1/courses"), other, "{\"title\":\"소프트웨어공학\"}"));
        enroll(other, se, "pkim@t.com");

        // 데이터베이스: 4회 마감 — kim 출석 1, 지각 3 → 인정 결석 1 → (4-1)/4 = 75%
        String[] kimMarks = {"PRESENT", "LATE", "LATE", "LATE"};
        for (String mark : kimMarks) {
            long s = idOf(call(post("/api/v1/courses/" + db + "/sessions"), prof,
                    "{\"scheduledAt\":\"2026-11-10T13:00:00\"," + ROOM + "}"));
            call(post("/api/v1/sessions/" + s + "/open"), prof, null).andExpect(status().isOk());
            call(put("/api/v1/sessions/" + s + "/attendances/" + myId(kim)), prof,
                    "{\"status\":\"" + mark + "\"}").andExpect(status().isOk());
            call(post("/api/v1/sessions/" + s + "/close"), prof, null).andExpect(status().isOk());
        }
        // 아직 안 한 회차(예정)는 계산에서 빠진다
        call(post("/api/v1/courses/" + db + "/sessions"), prof, "{\"scheduledAt\":\"2026-11-24T13:00:00\"," + ROOM + "}");

        call(get("/api/v1/me/profile"), kim, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김학생"))
                .andExpect(jsonPath("$.email").value("pkim@t.com"))
                .andExpect(jsonPath("$.courses.length()").value(2))
                .andExpect(jsonPath("$.courses[?(@.title=='데이터베이스')].totalSessions").value(4))
                .andExpect(jsonPath("$.courses[?(@.title=='데이터베이스')].presentCount").value(1))
                .andExpect(jsonPath("$.courses[?(@.title=='데이터베이스')].lateCount").value(3))
                .andExpect(jsonPath("$.courses[?(@.title=='데이터베이스')].convertedAbsences").value(1))
                .andExpect(jsonPath("$.courses[?(@.title=='데이터베이스')].attendanceRate").value(75.0))
                .andExpect(jsonPath("$.courses[?(@.title=='데이터베이스')].ownerName").value("김교수"))
                // 마감된 회차가 없는 교육은 출석률 없음(null)
                .andExpect(jsonPath("$.courses[?(@.title=='소프트웨어공학')].totalSessions").value(0))
                .andExpect(jsonPath("$.courses[?(@.title=='소프트웨어공학')].attendanceRate").value((Object) null));

        // 다른 학생은 자기 것만: lee 는 데이터베이스 하나, 4회 모두 자동 결석 → 0%
        call(get("/api/v1/me/profile"), lee, null)
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].absentCount").value(4))
                .andExpect(jsonPath("$.courses[0].attendanceRate").value(0.0));

        // 교수는 학생 프로필 API 를 쓸 수 없다
        call(get("/api/v1/me/profile"), prof, null).andExpect(status().isForbidden());

        // 관리자 프로필: 개설 교육 1개, 수강생 2명, 회차 5개 중 4개 마감
        call(get("/api/v1/me/admin-profile"), prof, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김교수"))
                .andExpect(jsonPath("$.courseCount").value(1))
                .andExpect(jsonPath("$.studentCount").value(2))
                .andExpect(jsonPath("$.courses[0].title").value("데이터베이스"))
                .andExpect(jsonPath("$.courses[0].sessionCount").value(5))
                .andExpect(jsonPath("$.courses[0].closedSessionCount").value(4));
        // 수강생은 관리자 프로필을 볼 수 없다
        call(get("/api/v1/me/admin-profile"), kim, null).andExpect(status().isForbidden());
    }

    // ---- 도우미 ----

    private void enroll(String token, long courseId, String email) throws Exception {
        call(post("/api/v1/courses/" + courseId + "/enrollments"), token, "{\"email\":\"" + email + "\"}")
                .andExpect(status().is2xxSuccessful());
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

    private String login(String email, String role, String name) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"" + name
                        + "\",\"role\":\"" + role + "\"}"));
        String res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asText();
    }
}
