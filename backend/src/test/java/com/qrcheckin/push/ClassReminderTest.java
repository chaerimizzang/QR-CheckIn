package com.qrcheckin.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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
import com.qrcheckin.support.MutableClock;

/** 수업 10분 전 알림(웹 푸시). 실제 푸시 서버 대신 요청을 기록하는 가짜 전송기로 시험하고, 암호문은 직접 풀어 확인한다 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:pushtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.checkin.close-check-ms=3600000",
        "app.push.check-ms=3600000"
})
class ClassReminderTest {

    static final Instant START = Instant.parse("2026-11-20T03:50:00Z"); // 12:50 KST

    /** 보낸 요청을 기록. endpoint 에 "gone" 이 있으면 410(구독 끊김)으로 답한다 */
    static class RecordingTransport implements PushTransport {
        record Sent(String endpoint, Map<String, String> headers, byte[] body) {
        }

        final List<Sent> sent = new ArrayList<>();

        @Override
        public synchronized int post(String endpoint, Map<String, String> headers, byte[] body) {
            sent.add(new Sent(endpoint, headers, body));
            return endpoint.contains("gone") ? 410 : 201;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START, ZoneId.of("Asia/Seoul"));
        }

        @Bean
        @Primary
        RecordingTransport recordingTransport() {
            return new RecordingTransport();
        }
    }

    /** 브라우저 쪽 구독(키 쌍 + auth) */
    record Browser(String endpoint, KeyPair keys, byte[] auth) {
        String json() {
            return "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\""
                    + WebPushCrypto.b64(WebPushCrypto.encodePublic((ECPublicKey) keys.getPublic()))
                    + "\",\"auth\":\"" + WebPushCrypto.b64(auth) + "\"}}";
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired RecordingTransport transport;
    @Autowired ClassReminderService reminders;
    @Autowired PushSubscriptionRepository subscriptions;

    @BeforeEach
    void reset() {
        clock.set(START);
        transport.sent.clear();
    }

    @Test
    void 수업_10분_전에_수강생과_교수에게_알린다() throws Exception {
        String prof = login("rprof@t.com", "ADMIN");
        String kim = login("rkim@t.com", "STUDENT");
        String outsider = login("rout@t.com", "STUDENT");
        long course = idOf(call(post("/api/v1/courses"), prof, "{\"title\":\"데이터베이스\"}"));
        call(post("/api/v1/courses/" + course + "/enrollments"), prof, "{\"email\":\"rkim@t.com\"}");
        long soon = idOf(call(post("/api/v1/courses/" + course + "/sessions"), prof, "{\"scheduledAt\":\"2026-11-20T13:00:00\"}"));
        call(post("/api/v1/courses/" + course + "/sessions"), prof, "{\"scheduledAt\":\"2026-11-20T15:00:00\"}");

        Browser kimPhone = browser("https://fcm.googleapis.com/fcm/send/kim-phone");
        Browser profPhone = browser("https://web.push.apple.com/prof-phone");
        Browser outsiderPhone = browser("https://fcm.googleapis.com/fcm/send/outsider");
        call(post("/api/v1/push/subscriptions"), kim, kimPhone.json()).andExpect(status().isNoContent());
        call(post("/api/v1/push/subscriptions"), prof, profPhone.json()).andExpect(status().isNoContent());
        call(post("/api/v1/push/subscriptions"), outsider, outsiderPhone.json()).andExpect(status().isNoContent());

        // 12:50 → 13:00 수업이 10분 안 → 수강생 1 + 교수 1. 수강하지 않는 학생과 15:00 수업은 제외
        reminders.sendDueReminders();
        assertThat(transport.sent).hasSize(2);
        RecordingTransport.Sent toKim = sentTo(kimPhone.endpoint());
        assertThat(toKim.headers()).containsEntry("Content-Encoding", "aes128gcm").containsEntry("TTL", "600");
        assertThat(toKim.headers().get("Authorization")).startsWith("vapid t=").contains(", k=");
        JsonNode msg = om.readTree(decrypt(kimPhone, toKim.body()));
        assertThat(msg.get("title").asText()).isEqualTo("다음 수업이 있어요 [데이터베이스]");
        assertThat(msg.get("body").asText()).isEqualTo("11/20(금) 13:00 시작 (10분 뒤)");
        assertThat(msg.get("url").asText()).isEqualTo("/student/courses/" + course);
        JsonNode toProf = om.readTree(decrypt(profPhone, sentTo(profPhone.endpoint()).body()));
        assertThat(toProf.get("url").asText()).isEqualTo("/admin/sessions/" + soon);

        // 같은 회차는 한 번만
        transport.sent.clear();
        clock.advance(Duration.ofMinutes(1));
        reminders.sendDueReminders();
        assertThat(transport.sent).isEmpty();

        // 14:50 이 되면 15:00 수업 알림
        clock.set(Instant.parse("2026-11-20T05:50:00Z"));
        reminders.sendDueReminders();
        assertThat(transport.sent).hasSize(2);
    }

    @Test
    void 끊긴_구독은_지우고_로그아웃한_기기는_끈다() throws Exception {
        String lee = login("rlee@t.com", "STUDENT");
        Browser gone = browser("https://fcm.googleapis.com/fcm/send/gone-1");
        Browser phone = browser("https://fcm.googleapis.com/fcm/send/lee-phone");
        call(post("/api/v1/push/subscriptions"), lee, gone.json());
        call(post("/api/v1/push/subscriptions"), lee, phone.json());

        call(post("/api/v1/push/test"), lee, null).andExpect(status().isOk()).andExpect(jsonPath("$.sent").value(1));
        assertThat(subscriptions.findByEndpoint(gone.endpoint())).isEmpty(); // 410 → 삭제

        call(post("/api/v1/push/subscriptions/delete"), lee, "{\"endpoint\":\"" + phone.endpoint() + "\"}")
                .andExpect(status().isNoContent());
        assertThat(subscriptions.findByEndpoint(phone.endpoint())).isEmpty();
    }

    @Test
    void 같은_기기로_다른_계정이_구독하면_주인이_바뀐다() throws Exception {
        String a = login("ra@t.com", "STUDENT");
        String b = login("rb@t.com", "STUDENT");
        Browser shared = browser("https://fcm.googleapis.com/fcm/send/shared");
        call(post("/api/v1/push/subscriptions"), a, shared.json());
        call(post("/api/v1/push/subscriptions"), b, shared.json());
        long aId = idOf(call(get("/api/v1/me"), a, null));
        long bId = idOf(call(get("/api/v1/me"), b, null));
        assertThat(subscriptions.countByUserId(aId)).isZero();
        assertThat(subscriptions.countByUserId(bId)).isEqualTo(1);
    }

    @Test
    void 잘못된_구독_정보는_400() throws Exception {
        String c = login("rc@t.com", "STUDENT");
        call(post("/api/v1/push/subscriptions"), c,
                "{\"endpoint\":\"http://evil.local/x\",\"keys\":{\"p256dh\":\"abc\",\"auth\":\"abc\"}}")
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/push/public-key").header("Authorization", "Bearer " + c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").isString());
    }

    // ---- 도우미 ----

    private RecordingTransport.Sent sentTo(String endpoint) {
        return transport.sent.stream().filter(s -> s.endpoint().equals(endpoint)).findFirst().orElseThrow();
    }

    private static Browser browser(String endpoint) {
        return new Browser(endpoint, WebPushCrypto.newKeyPair(), WebPushCrypto.randomBytes(16));
    }

    /** 브라우저가 하는 복호화(RFC 8291) — 서버 암호화가 표준대로인지 확인 */
    private static String decrypt(Browser browser, byte[] body) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buf.get(salt);
        buf.getInt(); // rs
        byte[] asPublic = new byte[buf.get()];
        buf.get(asPublic);
        byte[] cipher = new byte[buf.remaining()];
        buf.get(cipher);

        byte[] uaPublic = WebPushCrypto.encodePublic((ECPublicKey) browser.keys().getPublic());
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(browser.keys().getPrivate());
        ka.doPhase(WebPushCrypto.decodePublic(asPublic), true);
        byte[] prkKey = WebPushCrypto.hmac(browser.auth(), ka.generateSecret());
        byte[] ikm = WebPushCrypto.hmac(prkKey, WebPushCrypto.concat(
                "WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[] {1}));
        byte[] prk = WebPushCrypto.hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(WebPushCrypto.hmac(prk,
                "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)), 16);
        byte[] nonce = Arrays.copyOf(WebPushCrypto.hmac(prk,
                "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = gcm.doFinal(cipher);
        assertThat(plain[plain.length - 1]).isEqualTo((byte) 2);
        return new String(plain, 0, plain.length - 1, StandardCharsets.UTF_8);
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
