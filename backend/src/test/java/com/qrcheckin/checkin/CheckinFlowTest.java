package com.qrcheckin.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

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
import com.qrcheckin.attendance.Attendance;
import com.qrcheckin.attendance.AttendanceRepository;
import com.qrcheckin.attendance.AttendanceSource;
import com.qrcheckin.attendance.AttendanceStatus;
import com.qrcheckin.support.MutableClock;

/** 시나리오 B~D: 출석 시작 → QR 스캔 → 실패 안내 → 마감·자동 결석 (계획서 11.1 핵심 테스트 케이스) */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:checkintest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.checkin.close-check-ms=3600000" // 스케줄러는 테스트에서 직접 호출
})
class CheckinFlowTest {

    /** 2026-11-10 13:00:00 (Asia/Seoul) */
    static final Instant START = Instant.parse("2026-11-10T04:00:00Z");
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
    @Autowired MutableClock clock;
    @Autowired SessionRunService sessionRunService;
    @Autowired AttendanceRepository attendanceRepository;

    @BeforeEach
    void resetClock() {
        clock.set(START);
    }

    @Test
    void 출석_흐름_전체() throws Exception {
        String prof = signupAndLogin("prof@t.com", "ADMIN");
        String s1 = signupAndLogin("s1@t.com", "STUDENT");   // 정상 출석
        String s2 = signupAndLogin("s2@t.com", "STUDENT");   // 미등록
        String s3 = signupAndLogin("s3@t.com", "STUDENT");   // 실패만 하다가 결석

        long courseId = idOf(call(post("/api/v1/courses"), prof, "{\"title\":\"데이터베이스 실습\"}"));
        call(post("/api/v1/courses/" + courseId + "/enrollments"), prof, "{\"email\":\"s1@t.com\"}");
        call(post("/api/v1/courses/" + courseId + "/enrollments"), prof, "{\"email\":\"s3@t.com\"}");
        long sessionId = idOf(call(post("/api/v1/courses/" + courseId + "/sessions"), prof,
                "{\"scheduledAt\":\"2026-11-10T13:00:00\"," + ROOM + ",\"radiusM\":50,\"checkinMinutes\":5}"));

        // 시작 전에는 QR 없음
        call(get("/api/v1/sessions/" + sessionId + "/qr"), prof, null)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SESSION_NOT_OPEN"));

        // B-06 출석 시작 → 13:05 마감
        call(post("/api/v1/sessions/" + sessionId + "/open"), prof, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.closedAt").value("2026-11-10T13:05:00"));
        call(post("/api/v1/sessions/" + sessionId + "/open"), prof, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_ALREADY_STARTED"));
        // 학생은 QR 을 받을 수 없음
        call(get("/api/v1/sessions/" + sessionId + "/qr"), s1, null).andExpect(status().isForbidden());

        clock.advance(Duration.ofSeconds(2));
        String t0 = qrToken(prof, sessionId);

        // B-07 정상 출석 (강의실 좌표, 정확도 20m)
        checkIn(s1, t0, "37.5665", "126.978", 20)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.courseTitle").value("데이터베이스 실습"));
        // 성공 후 재스캔 → ALREADY_CHECKED
        checkIn(s1, t0, "37.5665", "126.978", 20)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CHECKED"))
                .andExpect(jsonPath("$.message").value(containsString("13:00")));
        // 미등록 학생 → NOT_ENROLLED
        checkIn(s2, t0, "37.5665", "126.978", 20)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ENROLLED"));
        // 약 111m 떨어짐 → OUT_OF_RANGE + 거리 안내
        checkIn(s3, t0, "37.5675", "126.978", 20)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.detail.distanceM").value(111))
                .andExpect(jsonPath("$.detail.hint").exists());
        // 정확도 150m → LOW_ACCURACY (거리가 가까워도)
        checkIn(s3, t0, "37.5665", "126.978", 150)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("LOW_ACCURACY"));
        // 위조·형식 오류 토큰 → QR_INVALID
        checkIn(s3, t0.substring(0, t0.length() - 2) + "xx", "37.5665", "126.978", 20)
                .andExpect(jsonPath("$.code").value("QR_INVALID"));
        checkIn(s3, "hello", "37.5665", "126.978", 20)
                .andExpect(jsonPath("$.code").value("QR_INVALID"));
        // 관리자는 출석 인증 불가
        checkIn(prof, t0, "37.5665", "126.978", 20).andExpect(status().isForbidden());

        // 32초 뒤(두 주기 지남) → 처음 토큰은 QR_EXPIRED, 새 토큰은 다름
        clock.advance(Duration.ofSeconds(30)); // 13:00:32 → window 2
        checkIn(s3, t0, "37.5665", "126.978", 20)
                .andExpect(jsonPath("$.code").value("QR_EXPIRED"));
        String t2 = qrToken(prof, sessionId);
        assertThat(t2).isNotEqualTo(t0);

        // QR 화면 정보: 출석 1명 / 수강생 2명
        call(get("/api/v1/sessions/" + sessionId + "/qr"), prof, null)
                .andExpect(jsonPath("$.presentCount").value(1))
                .andExpect(jsonPath("$.enrolledCount").value(2))
                .andExpect(jsonPath("$.url").value(containsString("/check-in?t=")));

        // 5분 0초 → 마감. 마감 직전 받은 토큰이어도 SESSION_CLOSED
        clock.set(START.plusSeconds(4 * 60 + 59));
        String last = qrToken(prof, sessionId);
        clock.set(START.plusSeconds(5 * 60));
        checkIn(s3, last, "37.5665", "126.978", 20)
                .andExpect(jsonPath("$.code").value("SESSION_CLOSED"));
        call(get("/api/v1/sessions/" + sessionId + "/qr"), prof, null)
                .andExpect(jsonPath("$.code").value("SESSION_CLOSED"));

        // B-08 자동 결석 — 두 번 실행해도 결과 같음(멱등)
        assertThat(sessionRunService.closeExpiredSessions()).isEqualTo(1);
        assertThat(sessionRunService.closeExpiredSessions()).isEqualTo(0);
        var records = attendanceRepository.findBySessionId(sessionId);
        assertThat(records).hasSize(2);
        assertThat(records).extracting(Attendance::getStatus)
                .containsExactlyInAnyOrder(AttendanceStatus.PRESENT, AttendanceStatus.ABSENT);
        assertThat(records).filteredOn(a -> a.getStatus() == AttendanceStatus.ABSENT)
                .extracting(Attendance::getSource).containsExactly(AttendanceSource.SYSTEM);

        call(get("/api/v1/sessions/" + sessionId), prof, null).andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void 장소를_지정하지_않으면_출석을_시작할_수_없고_출석_종료는_즉시_마감한다() throws Exception {
        String prof = signupAndLogin("prof2@t.com", "ADMIN");
        long courseId = idOf(call(post("/api/v1/courses"), prof, "{\"title\":\"알고리즘\"}"));
        long noLoc = idOf(call(post("/api/v1/courses/" + courseId + "/sessions"), prof,
                "{\"scheduledAt\":\"2026-11-11T09:00:00\"}"));
        call(post("/api/v1/sessions/" + noLoc + "/open"), prof, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));

        long withLoc = idOf(call(post("/api/v1/courses/" + courseId + "/sessions"), prof,
                "{\"scheduledAt\":\"2026-11-11T09:00:00\"," + ROOM + "}"));
        call(post("/api/v1/sessions/" + withLoc + "/open"), prof, null).andExpect(status().isOk());
        clock.advance(Duration.ofSeconds(40));
        call(post("/api/v1/sessions/" + withLoc + "/close"), prof, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        // 이미 마감된 회차는 수정 불가
        call(put("/api/v1/sessions/" + withLoc),
                prof, "{\"scheduledAt\":\"2026-11-11T09:00:00\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_NOT_EDITABLE"));
    }

    private ResultActions checkIn(String token, String qr, String lat, String lon, int accuracy) throws Exception {
        return call(post("/api/v1/check-in"), token, "{\"token\":\"" + qr + "\",\"latitude\":" + lat
                + ",\"longitude\":" + lon + ",\"accuracy\":" + accuracy + "}");
    }

    private String qrToken(String prof, long sessionId) throws Exception {
        String res = call(get("/api/v1/sessions/" + sessionId + "/qr"), prof, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("token").asText();
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

    private String signupAndLogin(String email, String role) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"" + email
                        + "\",\"role\":\"" + role + "\"}"));
        String res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asText();
    }
}
