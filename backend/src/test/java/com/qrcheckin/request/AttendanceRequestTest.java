package com.qrcheckin.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrcheckin.support.MutableClock;

/** 출석 인정(공결·정정 신청) */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:requesttest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.checkin.close-check-ms=3600000",
        "app.push.check-ms=3600000"
})
class AttendanceRequestTest {

    static final Instant START = Instant.parse("2026-11-20T04:00:00Z"); // 13:00 KST
    static final String ROOM = "\"latitude\":37.5665,\"longitude\":126.978";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START, ZoneId.of("Asia/Seoul"));
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
    /** 마감된 회차 2개(kim: 1회차 결석, 2회차 지각) + 예정 회차 1개 */
    long closed1;
    long closed2;
    long scheduled;

    @BeforeEach
    void seed() throws Exception {
        n = String.valueOf(SEQ.incrementAndGet());
        prof = login("qprof" + n + "@t.com", "ADMIN");
        kim = login("qkim" + n + "@t.com", "STUDENT");
        kimId = idOf(call(get("/api/v1/me"), kim, null));
        course = idOf(call(post("/api/v1/courses"), prof, "{\"title\":\"데이터베이스\"}"));
        call(post("/api/v1/courses/" + course + "/enrollments"), prof, "{\"email\":\"qkim" + n + "@t.com\"}");

        closed1 = runSession("2026-11-06T13:00:00", null);    // 표시 안 함 → 마감 때 자동 결석
        closed2 = runSession("2026-11-13T13:00:00", "LATE");
        scheduled = idOf(call(post("/api/v1/courses/" + course + "/sessions"), prof,
                "{\"scheduledAt\":\"2026-11-27T13:00:00\"," + ROOM + "}"));
    }

    @Test
    void 공결_신청_승인하면_공결이_되고_출석률에서_빠진다() throws Exception {
        // 출석률: 마감 2회 중 결석 1 → 50%
        call(get("/api/v1/me/profile"), kim, null).andExpect(jsonPath("$.courses[0].attendanceRate").value(50.0));

        MockMultipartFile photo = new MockMultipartFile("file", "진단서.jpg", "image/jpeg", new byte[] {1, 2, 3});
        long id = idOf(submit(closed1, "EXCUSE", null, "병원 진료", photo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedStatus").value("EXCUSED"))
                .andExpect(jsonPath("$.statusAtRequest").value("ABSENT"))
                .andExpect(jsonPath("$.hasEvidence").value(true)));

        // 같은 회차에 또 신청하면 409
        submit(closed1, "EXCUSE", null, "또", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_DUPLICATED"));

        // 교수: 목록·대기 수·증빙
        call(get("/api/v1/requests/pending-count"), prof, null).andExpect(jsonPath("$.pending").value(1));
        call(get("/api/v1/requests"), prof, null)
                .andExpect(jsonPath("$[0].studentName").value("qkim" + n + "@t.com"))
                .andExpect(jsonPath("$[0].courseTitle").value("데이터베이스"))
                .andExpect(jsonPath("$[0].sessionNo").value(1));
        call(get("/api/v1/requests/" + id + "/evidence"), prof, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));

        // 승인 → 출결이 공결로, 수정 이력이 남고, 출석률은 마감 1회(지각 1) 기준 100%
        call(post("/api/v1/requests/" + id + "/approve"), prof, "{\"comment\":\"확인했어요\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewComment").value("확인했어요"));
        call(get("/api/v1/sessions/" + closed1 + "/attendances"), prof, null)
                .andExpect(jsonPath("$.summary.excused").value(1))
                .andExpect(jsonPath("$.students[0].status").value("EXCUSED"));
        call(get("/api/v1/sessions/" + closed1 + "/attendances/" + kimId + "/history"), prof, null)
                .andExpect(jsonPath("$[0].newStatus").value("EXCUSED"));
        call(get("/api/v1/me/profile"), kim, null)
                .andExpect(jsonPath("$.courses[0].excusedCount").value(1))
                .andExpect(jsonPath("$.courses[0].attendanceRate").value(100.0));

        // 이미 처리된 신청은 다시 처리할 수 없다
        call(post("/api/v1/requests/" + id + "/reject"), prof, "{\"comment\":\"x\"}")
                .andExpect(status().isConflict());
        call(get("/api/v1/requests/pending-count"), prof, null).andExpect(jsonPath("$.pending").value(0));
    }

    @Test
    void 정정_신청_반려는_사유가_필요하고_출결은_그대로() throws Exception {
        long id = idOf(submit(closed2, "CORRECTION", "PRESENT", "제때 찍었는데 지각으로 나왔어요", null)
                .andExpect(jsonPath("$.requestedStatus").value("PRESENT"))
                .andExpect(jsonPath("$.statusAtRequest").value("LATE")));

        call(post("/api/v1/requests/" + id + "/reject"), prof, "{}").andExpect(status().isBadRequest());
        call(post("/api/v1/requests/" + id + "/reject"), prof, "{\"comment\":\"QR 기록이 13:12예요\"}")
                .andExpect(jsonPath("$.status").value("REJECTED"));
        call(get("/api/v1/me/requests"), kim, null)
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].reviewComment").value("QR 기록이 13:12예요"));
        call(get("/api/v1/sessions/" + closed2 + "/attendances"), prof, null)
                .andExpect(jsonPath("$.students[0].status").value("LATE"));

        // 반려된 뒤에는 다시 신청할 수 있다
        submit(closed2, "CORRECTION", "PRESENT", "출입 기록 첨부", null).andExpect(status().isOk());
    }

    @Test
    void 미리_공결_신청하면_예정_회차도_승인되고_마감때_결석으로_덮이지_않는다() throws Exception {
        // 정정은 열리지 않은 회차에 못 함
        submit(scheduled, "CORRECTION", "PRESENT", "x", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_ALLOWED"));

        long id = idOf(submit(scheduled, "EXCUSE", null, "예비군 훈련", null));
        call(post("/api/v1/requests/" + id + "/approve"), prof, null).andExpect(status().isOk());

        // 회차를 열고 마감해도 공결 유지
        call(post("/api/v1/sessions/" + scheduled + "/open"), prof, null).andExpect(status().isOk());
        call(post("/api/v1/sessions/" + scheduled + "/close"), prof, null).andExpect(status().isOk());
        call(get("/api/v1/sessions/" + scheduled + "/attendances"), prof, null)
                .andExpect(jsonPath("$.students[0].status").value("EXCUSED"));
    }

    @Test
    void 권한과_입력_검사() throws Exception {
        String other = login("qother" + n + "@t.com", "STUDENT");
        String otherProf = login("qop" + n + "@t.com", "ADMIN");

        // 수강하지 않는 학생은 신청 불가
        call(multipart("/api/v1/sessions/" + closed1 + "/requests").param("type", "EXCUSE").param("reason", "x"),
                other, null).andExpect(status().isForbidden());
        // 사유 없음, 지원하지 않는 파일
        submit(closed1, "EXCUSE", null, "  ", null).andExpect(status().isBadRequest());
        submit(closed1, "EXCUSE", null, "x", new MockMultipartFile("file", "a.html", "text/html", new byte[] {1}))
                .andExpect(status().isBadRequest());

        long id = idOf(submit(closed1, "EXCUSE", null, "가족 행사", null));
        // 남의 교육 교수는 승인·조회 불가, 다른 학생은 증빙 조회 불가
        call(post("/api/v1/requests/" + id + "/approve"), otherProf, null).andExpect(status().isForbidden());
        call(get("/api/v1/requests/" + id + "/evidence"), other, null).andExpect(status().isForbidden());
        call(get("/api/v1/requests"), otherProf, null).andExpect(jsonPath("$.length()").value(0));

        // 본인 취소
        call(post("/api/v1/requests/" + id + "/cancel"), kim, null).andExpect(jsonPath("$.status").value("CANCELED"));
        call(post("/api/v1/requests/" + id + "/approve"), prof, null).andExpect(status().isConflict());
    }

    @Test
    void 신청이_있어도_교육_수강생_회차를_지울_수_있다() throws Exception {
        long id = idOf(submit(scheduled, "EXCUSE", null, "예비군", null));
        call(post("/api/v1/requests/" + id + "/approve"), prof, null);
        submit(closed1, "EXCUSE", null, "병원", new MockMultipartFile("file", "a.png", "image/png", new byte[] {9}));

        call(delete("/api/v1/sessions/" + scheduled), prof, null).andExpect(status().is2xxSuccessful());
        call(delete("/api/v1/courses/" + course + "/enrollments/" + kimId), prof, null)
                .andExpect(status().is2xxSuccessful());
        call(delete("/api/v1/courses/" + course), prof, null).andExpect(status().is2xxSuccessful());
    }

    @Test
    void 교수가_직접_공결로_바꿀_수_있다() throws Exception {
        call(put("/api/v1/sessions/" + closed1 + "/attendances/" + kimId), prof, "{\"status\":\"EXCUSED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXCUSED"));
        call(get("/api/v1/courses/" + course + "/attendance/export"), prof, null)
                .andExpect(status().isOk());
    }

    // ---- 도우미 ----

    private ResultActions submit(long sessionId, String type, String requested, String reason, MockMultipartFile file)
            throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/sessions/" + sessionId + "/requests");
        req.param("type", type).param("reason", reason);
        if (requested != null) {
            req.param("requestedStatus", requested);
        }
        if (file != null) {
            req.file(file);
        }
        return call(req, kim, null);
    }

    /** 회차를 만들어 열고, mark 가 있으면 kim 을 그 상태로 표시한 뒤 마감한다 */
    private long runSession(String at, String mark) throws Exception {
        long s = idOf(call(post("/api/v1/courses/" + course + "/sessions"), prof,
                "{\"scheduledAt\":\"" + at + "\"," + ROOM + "}"));
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
