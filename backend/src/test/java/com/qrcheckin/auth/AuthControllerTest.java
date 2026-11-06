package com.qrcheckin.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** B-01 회원가입 → 로그인 → /me 흐름 (메모리 H2 사용) */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:authtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private static final String SIGNUP = """
            {"email":"Student1@Test.com","password":"password123","name":"김학생","studentNo":"20230001","role":"STUDENT"}
            """;

    @Test
    void 가입_로그인_내정보() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("student1@test.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"));

        // 같은 이메일(대소문자만 다름) 재가입 → 409
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));

        // 틀린 비밀번호 → 401 LOGIN_FAILED
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student1@test.com\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));

        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student1@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        JsonNode body = om.readTree(login.getResponse().getContentAsString());
        String token = body.get("accessToken").asText();

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김학생"));
    }

    @Test
    void 토큰_없이_보호된_API_호출하면_401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 입력값_오류는_400_VALIDATION_FAILED() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-email\",\"password\":\"123\",\"name\":\"\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail.fields.email").exists());
    }
}
