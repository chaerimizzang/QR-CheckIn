package com.qrcheckin.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
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

/** 학기 · 출결 확정 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:semestertest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.checkin.close-check-ms=3600000",
        "app.push.check-ms=3600000",
        "app.attendance.warning-check-ms=3600000"
})
class SemesterTest {

    static final String ROOM = "\"latitude\":37.5665,\"longitude\":126.978";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-11-20T04:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void 날짜로_학기를_정한다() {
        assertThat(Semester.of(LocalDate.of(2026, 3, 2))).isEqualTo(new Semester(2026, SemesterTerm.SPRING));
        assertThat(Semester.of(LocalDate.of(2026, 7, 1))).isEqualTo(new Semester(2026, SemesterTerm.SUMMER));
        assertThat(Semester.of(LocalDate.of(2026, 12, 20))).isEqualTo(new Semester(2026, SemesterTerm.FALL));
        assertThat(Semester.of(LocalDate.of(2027, 1, 10))).isEqualTo(new Semester(2026, SemesterTerm.WINTER));
        assertThat(new Semester(2026, SemesterTerm.FALL).label()).isEqualTo("2026년 2학기");
    }

    @Test
    void 교육에_학기가_붙고_학기_목록이_나온다() throws Exception {
        String prof = login("sprof@t.com", "ADMIN");
        call(post("/api/v1/courses"), prof, "{\"title\":\"가을\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-12-31\"}")
                .andExpect(jsonPath("$.semesterKey").value("2026-FALL"))
                .andExpect(jsonPath("$.semesterLabel").value("2026년 2학기"));
        call(post("/api/v1/courses"), prof, "{\"title\":\"봄\",\"semesterYear\":2026,\"semesterTerm\":\"SPRING\"}")
                .andExpect(jsonPath("$.semesterKey").value("2026-SPRING"));
        call(post("/api/v1/courses"), prof, "{\"title\":\"날짜 없음\"}")
                .andExpect(jsonPath("$.semesterKey").value("2026-FALL")); // 오늘(11월) 기준

        call(get("/api/v1/semesters/current"), prof, null).andExpect(jsonPath("$.key").value("2026-FALL"));
        call(get("/api/v1/semesters"), prof, null)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].key").value("2026-FALL"))
                .andExpect(jsonPath("$[1].key").value("2026-SPRING"));
    }

    @Test
    void 출결_확정하면_읽기_전용이고_확정_취소로_되돌린다() throws Exception {
        String prof = login("fprof@t.com", "ADMIN");
        String kim = login("fkim@t.com", "STUDENT");
        login("flee@t.com", "STUDENT");
        long kimId = idOf(call(get("/api/v1/me"), kim, null));
        long course = idOf(call(post("/api/v1/courses"), prof, "{\"title\":\"데이터베이스\"}"));
        call(post("/api/v1/courses/" + course + "/enrollments"), prof, "{\"email\":\"fkim@t.com\"}");
        long s1 = idOf(call(post("/api/v1/courses/" + course + "/sessions"), prof,
                "{\"scheduledAt\":\"2026-11-13T13:00:00\"," + ROOM + "}"));
        long s2 = idOf(call(post("/api/v1/courses/" + course + "/sessions"), prof,
                "{\"scheduledAt\":\"2026-12-04T13:00:00\"," + ROOM + "}"));

        // 진행 중 회차가 있으면 확정 불가
        call(post("/api/v1/sessions/" + s1 + "/open"), prof, null).andExpect(status().isOk());
        call(post("/api/v1/courses/" + course + "/finalize"), prof, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FINALIZE_BLOCKED"));
        call(post("/api/v1/sessions/" + s1 + "/close"), prof, null).andExpect(status().isOk());

        // 처리 안 한 출석 인정 신청이 있으면 확정 불가
        long req = idOf(call(multipart("/api/v1/sessions/" + s1 + "/requests").param("type", "EXCUSE")
                .param("reason", "병원"), kim, null));
        call(get("/api/v1/courses/" + course + "/finalize-check"), prof, null)
                .andExpect(jsonPath("$.pendingRequests").value(1))
                .andExpect(jsonPath("$.unopenedSessions").value(1));
        call(post("/api/v1/courses/" + course + "/finalize"), prof, null).andExpect(status().isConflict());
        call(post("/api/v1/requests/" + req + "/approve"), prof, null).andExpect(status().isOk());

        // 확정 → 열지 않은 2회차는 지워짐
        call(post("/api/v1/courses/" + course + "/finalize"), prof, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.finalizedAt").isNotEmpty());
        call(get("/api/v1/courses/" + course + "/sessions"), prof, null).andExpect(jsonPath("$.length()").value(1));
        call(get("/api/v1/sessions/" + s2), prof, null).andExpect(status().isNotFound());

        // 바꾸는 요청은 전부 409, 조회·내려받기는 된다
        String locked = "COURSE_FINALIZED";
        call(put("/api/v1/sessions/" + s1 + "/attendances/" + kimId), prof, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(locked));
        call(post("/api/v1/courses/" + course + "/sessions"), prof, "{\"scheduledAt\":\"2026-12-11T13:00:00\"}")
                .andExpect(jsonPath("$.code").value(locked));
        call(post("/api/v1/courses/" + course + "/enrollments"), prof, "{\"email\":\"flee@t.com\"}")
                .andExpect(jsonPath("$.code").value(locked));
        call(delete("/api/v1/courses/" + course + "/enrollments/" + kimId), prof, null)
                .andExpect(jsonPath("$.code").value(locked));
        call(multipart("/api/v1/sessions/" + s1 + "/requests").param("type", "CORRECTION")
                .param("requestedStatus", "PRESENT").param("reason", "x"), kim, null)
                .andExpect(jsonPath("$.code").value(locked));
        call(delete("/api/v1/courses/" + course), prof, null).andExpect(jsonPath("$.code").value(locked));
        call(get("/api/v1/sessions/" + s1 + "/attendances"), prof, null).andExpect(status().isOk());
        call(get("/api/v1/courses/" + course + "/attendance/export"), prof, null).andExpect(status().isOk());
        call(get("/api/v1/courses/" + course), kim, null).andExpect(jsonPath("$.finalizedAt").isNotEmpty());

        // 확정 취소 → 다시 바꿀 수 있다
        call(post("/api/v1/courses/" + course + "/reopen"), prof, null).andExpect(jsonPath("$.finalizedAt").isEmpty());
        call(put("/api/v1/sessions/" + s1 + "/attendances/" + kimId), prof, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isOk());
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
