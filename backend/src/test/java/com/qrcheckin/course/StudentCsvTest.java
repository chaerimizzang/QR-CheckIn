package com.qrcheckin.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/** D-02 CSV 수강생 등록 · D-10 출결부 내보내기 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:csvtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.checkin.close-check-ms=3600000"
})
class StudentCsvTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void CSV로_수강생을_등록하고_결과를_알려준다() throws Exception {
        String prof = login("csvprof@t.com", "ADMIN", "김교수", null);
        login("csv1@t.com", "STUDENT", "김학생", "20240001");
        login("csv2@t.com", "STUDENT", "이학생", "20240002");
        login("csv3@t.com", "STUDENT", "박학생", "20240003");
        long c = courseId(prof, "자료구조");

        // UTF-8(BOM) — 성공 1, 실패 4(이름 불일치·연락처 형식·미가입·파일 안 중복)
        String csv = "﻿학번,이름,연락처\n"
                + "20240001,김학생,010-1234-5678\n"
                + "20240002,홍길동,010-1111-2222\n"
                + "20240003,박학생,02-123-4567\n"
                + "29999999,없는사람,010-0000-0000\n"
                + "20240001,김학생,010-1234-5678\n";
        upload(prof, c, csv.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(4))
                .andExpect(jsonPath("$.failures[0].studentNo").value("20240002"));

        // 엑셀 기본 'CSV(쉼표로 분리)' = CP949 로 저장된 파일도 읽는다
        String excel = "학번,이름,연락처\r\n20240003,박학생,01099998888\r\n";
        upload(prof, c, excel.getBytes(Charset.forName("MS949")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1));

        // 첫 줄(열 이름)이 다르거나 빈 파일이면 400 과 안내 문구(서버 오류 500 이 아니라)
        upload(prof, c, "번호,성명\n1,김\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV 첫 줄에는 학번,이름,연락처가 필요해요"));
        upload(prof, c, new byte[0]).andExpect(status().isBadRequest());

        // 등록 결과: 김학생, 박학생
        mvc.perform(get("/api/v1/courses/" + c + "/enrollments").header("Authorization", "Bearer " + prof))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 출결부와_출석률을_CSV로_내려받는다() throws Exception {
        String prof = login("exprof@t.com", "ADMIN", "김교수", null);
        login("ex1@t.com", "STUDENT", "최학생", "20249999");
        long c = courseId(prof, "운영체제");
        mvc.perform(post("/api/v1/courses/" + c + "/enrollments").header("Authorization", "Bearer " + prof)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"ex1@t.com\"}"));
        String sres = mvc.perform(post("/api/v1/courses/" + c + "/sessions").header("Authorization", "Bearer " + prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scheduledAt\":\"2026-11-20T13:00:00\"}"))
                .andReturn().getResponse().getContentAsString();
        long s1 = om.readTree(sres).get("id").asLong();
        String me = mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + login("ex1@t.com", "STUDENT", "최학생", "20249999")))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(put("/api/v1/sessions/" + s1 + "/attendances/" + om.readTree(me).get("id").asLong())
                .header("Authorization", "Bearer " + prof)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LATE\"}"))
                .andExpect(status().isOk());

        for (String path : new String[]{"/attendance/export", "/attendance/rate/export"}) {
            byte[] body = mvc.perform(get("/api/v1/courses/" + c + path).header("Authorization", "Bearer " + prof))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            String text = new String(body, StandardCharsets.UTF_8);
            assertThat(text).startsWith("﻿").contains("최학생"); // 엑셀에서 한글이 깨지지 않게 BOM
        }
    }

    // ---- 도우미 ----

    private org.springframework.test.web.servlet.ResultActions upload(String token, long courseId, byte[] content)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", content);
        return mvc.perform(multipart("/api/v1/courses/" + courseId + "/enrollments/csv").file(file)
                .header("Authorization", "Bearer " + token));
    }

    private long courseId(String token, String title) throws Exception {
        String res = mvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("id").asLong();
    }

    private String login(String email, String role, String name, String studentNo) throws Exception {
        String no = studentNo == null ? "" : ",\"studentNo\":\"" + studentNo + "\"";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"" + name + "\""
                        + no + ",\"role\":\"" + role + "\"}"));
        String res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asText();
    }
}
