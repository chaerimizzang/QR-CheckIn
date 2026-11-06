package com.qrcheckin.course;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

/** 시나리오 A(관리자 수업 준비): B-02 교육 → B-04 수강생 → B-03/B-05 회차·출석 조건 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:coursetest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CourseSessionFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void 관리자_수업_준비_시나리오() throws Exception {
        String prof = signupAndLogin("prof@test.com", "ADMIN");
        String other = signupAndLogin("other@test.com", "ADMIN");
        String stu = signupAndLogin("stu@test.com", "STUDENT");

        // B-02 교육 생성
        long courseId = idOf(call(post("/api/v1/courses"), prof,
                "{\"title\":\"데이터베이스 실습\",\"startDate\":\"2026-11-09\",\"endDate\":\"2026-12-18\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("데이터베이스 실습")));

        // 수강생은 교육을 만들 수 없음 → 403
        call(post("/api/v1/courses"), stu, "{\"title\":\"x\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // 다른 관리자는 남의 교육을 수정할 수 없음 → 403
        call(put("/api/v1/courses/" + courseId), other, "{\"title\":\"탈취\"}")
                .andExpect(status().isForbidden());
        // 종료일이 시작일보다 빠르면 400
        call(post("/api/v1/courses"), prof, "{\"title\":\"x\",\"startDate\":\"2026-12-01\",\"endDate\":\"2026-11-01\"}")
                .andExpect(status().isBadRequest());

        // B-04 수강생 추가
        call(post("/api/v1/courses/" + courseId + "/enrollments"), prof, "{\"email\":\"STU@test.com\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("stu@test.com"));
        call(post("/api/v1/courses/" + courseId + "/enrollments"), prof, "{\"email\":\"stu@test.com\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));
        call(post("/api/v1/courses/" + courseId + "/enrollments"), prof, "{\"email\":\"nobody@test.com\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        call(post("/api/v1/courses/" + courseId + "/enrollments"), prof, "{\"email\":\"other@test.com\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_A_STUDENT"));

        // 학생의 교육 목록에 보임, 수강 인원 1명
        call(get("/api/v1/courses"), stu, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(courseId))
                .andExpect(jsonPath("$[0].studentCount").value(1));

        // B-03 + B-05 회차 등록(장소 없이, 기본값 50m·5분)
        long s1 = idOf(call(post("/api/v1/courses/" + courseId + "/sessions"), prof,
                "{\"scheduledAt\":\"2026-11-10T13:00:00\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionNo").value(1))
                .andExpect(jsonPath("$.radiusM").value(50))
                .andExpect(jsonPath("$.checkinMinutes").value(5))
                .andExpect(jsonPath("$.status").value("SCHEDULED")));

        // 출석 조건 수정: 현재 위치로 지정 + 반경 80m
        call(put("/api/v1/sessions/" + s1), prof,
                "{\"scheduledAt\":\"2026-11-10T13:00:00\",\"latitude\":37.5665,\"longitude\":126.978,\"radiusM\":80,\"checkinMinutes\":5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(37.5665))
                .andExpect(jsonPath("$.radiusM").value(80));

        // 위도만 있으면 400, 반경 5m 는 범위 밖 400
        call(put("/api/v1/sessions/" + s1), prof, "{\"scheduledAt\":\"2026-11-10T13:00:00\",\"latitude\":37.5}")
                .andExpect(status().isBadRequest());
        call(put("/api/v1/sessions/" + s1), prof, "{\"scheduledAt\":\"2026-11-10T13:00:00\",\"radiusM\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.fields.radiusM").exists());

        // 두 번째 회차는 sessionNo 2
        call(post("/api/v1/courses/" + courseId + "/sessions"), prof, "{\"scheduledAt\":\"2026-11-17T13:00:00\"}")
                .andExpect(jsonPath("$.sessionNo").value(2));
        call(get("/api/v1/courses/" + courseId + "/sessions"), prof, null)
                .andExpect(jsonPath("$.length()").value(2));
        // 학생은 회차 관리 API 접근 불가
        call(get("/api/v1/courses/" + courseId + "/sessions"), stu, null).andExpect(status().isForbidden());

        // 수강생 삭제 → 학생 목록에서 사라짐
        long stuId = om.readTree(call(get("/api/v1/me"), stu, null).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
        call(delete("/api/v1/courses/" + courseId + "/enrollments/" + stuId), prof, null)
                .andExpect(status().isNoContent());
        call(get("/api/v1/courses"), stu, null).andExpect(jsonPath("$.length()").value(0));

        // 교육 삭제(회차까지 함께) → 404
        call(delete("/api/v1/courses/" + courseId), prof, null).andExpect(status().isNoContent());
        call(get("/api/v1/courses/" + courseId), prof, null).andExpect(status().isNotFound());
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
                        + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated());
        String res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asText();
    }
}
