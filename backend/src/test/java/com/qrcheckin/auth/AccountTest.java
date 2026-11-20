package com.qrcheckin.auth;

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

import com.fasterxml.jackson.databind.ObjectMapper;

/** 프로필의 계정 관리: 이름 변경 · 비밀번호 변경 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:accounttest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void 이름을_바꾸면_내_정보에_반영된다() throws Exception {
        String token = signupAndLogin("name@t.com", "password123");

        send(put("/api/v1/me/name"), token, "{\"name\":\"  새이름  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.name").value("새이름"));

        send(put("/api/v1/me/name"), token, "{\"name\":\"   \"}").andExpect(status().isBadRequest());
        send(put("/api/v1/me/name"), token, "{\"name\":\"" + "가".repeat(51) + "\"}").andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/me/name").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비밀번호_변경_오류는_400과_안내_문구로_답한다() throws Exception {
        String token = signupAndLogin("pw@t.com", "password123");

        send(post("/api/v1/me/password"), token, pw("wrong-pass", "newpass1234", "newpass1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"))
                .andExpect(jsonPath("$.message").value("현재 비밀번호가 맞지 않아요"));
        send(post("/api/v1/me/password"), token, pw("password123", "newpass1234", "different99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("새 비밀번호 두 칸이 서로 달라요"));
        send(post("/api/v1/me/password"), token, pw("password123", "password123", "password123"))
                .andExpect(status().isBadRequest());

        send(post("/api/v1/me/password"), token, pw("password123", "newpass1234", "newpass1234"))
                .andExpect(status().isNoContent());
        login("pw@t.com", "newpass1234").andExpect(status().isOk());
        login("pw@t.com", "password123").andExpect(status().isUnauthorized());
    }

    // ---- 도우미 ----

    private static String pw(String current, String next, String confirm) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next
                + "\",\"confirmPassword\":\"" + confirm + "\"}";
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               String token, String body) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String signupAndLogin(String email, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                        + "\",\"name\":\"원래이름\",\"role\":\"STUDENT\",\"studentNo\":\"20240099\"}"));
        String res = login(email, password).andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asText();
    }
}
