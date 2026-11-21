package com.qrcheckin.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import com.qrcheckin.push.PushTransport;
import com.qrcheckin.push.WebPushCrypto;
import com.qrcheckin.support.MutableClock;

/** 결석 경고(F 기준: 인정 결석이 전체 회차의 1/4 초과) */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:warningtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.checkin.close-check-ms=3600000",
        "app.push.check-ms=3600000",
        "app.attendance.warning-check-ms=3600000"
})
class AbsenceWarningTest {

    static final String ROOM = "\"latitude\":37.5665,\"longitude\":126.978";

    static class CountingTransport implements PushTransport {
        final List<String> endpoints = new ArrayList<>();

        @Override
        public synchronized int post(String endpoint, Map<String, String> headers, byte[] body) {
            endpoints.add(endpoint);
            return 201;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-11-20T04:00:00Z"), ZoneId.of("Asia/Seoul"));
        }

        @Bean
        @Primary
        CountingTransport countingTransport() {
            return new CountingTransport();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired CountingTransport transport;
    @Autowired AbsenceWarningService warnings;

    @Test
    void 단계별_경고와_알림() {
        // 규칙만: 16회 → 허용 4회
        assertThat(AbsenceRule.of(16, 0, 0.25).level()).isEqualTo(AbsenceRule.Level.SAFE);
        assertThat(AbsenceRule.of(16, 3, 0.25).level()).isEqualTo(AbsenceRule.Level.WARNING);
        assertThat(AbsenceRule.of(16, 4, 0.25).level()).isEqualTo(AbsenceRule.Level.DANGER);
        assertThat(AbsenceRule.of(16, 5, 0.25).level()).isEqualTo(AbsenceRule.Level.FAIL);
        assertThat(AbsenceRule.of(4, 0, 0.25).level()).isEqualTo(AbsenceRule.Level.SAFE); // 결석 0이면 경고 없음
        assertThat(AbsenceRule.of(0, 3, 0.25).level()).isEqualTo(AbsenceRule.Level.NONE);
    }

    @Test
    void 결석이_쌓이면_학생과_교수에게_한번씩_알린다() throws Exception {
        String prof = login("wprof@t.com", "ADMIN");
        String kim = login("wkim@t.com", "STUDENT");
        long kimId = idOf(call(get("/api/v1/me"), kim, null));
        long course = idOf(call(post("/api/v1/courses"), prof, "{\"title\":\"데이터베이스\"}"));
        call(post("/api/v1/courses/" + course + "/enrollments"), prof, "{\"email\":\"wkim@t.com\"}");
        long[] s = new long[4];
        for (int i = 0; i < 4; i++) { // 전체 4회 → 허용 결석 1회
            s[i] = idOf(call(post("/api/v1/courses/" + course + "/sessions"), prof,
                    "{\"scheduledAt\":\"2026-11-1" + i + "T13:00:00\"," + ROOM + "}"));
        }
        subscribe(kim, "https://fcm.googleapis.com/fcm/send/w-kim");
        subscribe(prof, "https://fcm.googleapis.com/fcm/send/w-prof");

        // 결석 없음 → 알림 없음
        warnings.checkAll();
        assertThat(transport.endpoints).isEmpty();

        // 1회차 결석(마감 때 자동) → 허용 1회에 닿음 = 위험 → 학생·교수 둘 다
        runAndClose(prof, s[0]);
        warnings.checkAll();
        assertThat(transport.endpoints).containsExactlyInAnyOrder(
                "https://fcm.googleapis.com/fcm/send/w-kim", "https://fcm.googleapis.com/fcm/send/w-prof");
        call(get("/api/v1/me/profile"), kim, null)
                .andExpect(jsonPath("$.courses[0].warningLevel").value("DANGER"))
                .andExpect(jsonPath("$.courses[0].remainingAbsences").value(0));
        call(get("/api/v1/courses/" + course + "/attendance/rate"), prof, null)
                .andExpect(jsonPath("$.plannedSessions").value(4))
                .andExpect(jsonPath("$.allowedAbsences").value(1))
                .andExpect(jsonPath("$.students[0].warningLevel").value("DANGER"));

        // 같은 단계면 다시 보내지 않는다
        transport.endpoints.clear();
        warnings.checkAll();
        assertThat(transport.endpoints).isEmpty();

        // 2회차도 결석 → F 기준 초과 → 다시 알림
        runAndClose(prof, s[1]);
        warnings.checkAll();
        assertThat(transport.endpoints).hasSize(2);

        // 출석으로 바꾸면(출석 인정 등) 단계가 내려가고 알림은 없다
        transport.endpoints.clear();
        call(put("/api/v1/sessions/" + s[1] + "/attendances/" + kimId), prof, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isOk());
        warnings.checkAll();
        assertThat(transport.endpoints).isEmpty();
        call(get("/api/v1/me/profile"), kim, null).andExpect(jsonPath("$.courses[0].warningLevel").value("DANGER"));
    }

    // ---- 도우미 ----

    private void runAndClose(String prof, long sessionId) throws Exception {
        call(post("/api/v1/sessions/" + sessionId + "/open"), prof, null).andExpect(status().isOk());
        call(post("/api/v1/sessions/" + sessionId + "/close"), prof, null).andExpect(status().isOk());
    }

    private void subscribe(String token, String endpoint) throws Exception {
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        String p256dh = WebPushCrypto.b64(WebPushCrypto.encodePublic((ECPublicKey) WebPushCrypto.newKeyPair().getPublic()));
        call(post("/api/v1/push/subscriptions"), token, "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\""
                + p256dh + "\",\"auth\":\"" + WebPushCrypto.b64(auth) + "\"}}").andExpect(status().isNoContent());
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
