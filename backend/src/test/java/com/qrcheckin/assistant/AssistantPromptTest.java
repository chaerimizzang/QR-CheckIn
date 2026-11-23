package com.qrcheckin.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** 지시문 만들기: '%' 가 들어 있어도 예외 없이 오늘 날짜·시각·출결 요약이 들어간다 */
class AssistantPromptTest {

    @Test
    void 퍼센트가_있어도_지시문을_만든다() {
        String s = AssistantPrompt.system(LocalDateTime.of(2026, 11, 23, 9, 5),
                "[{\"courseId\":6,\"title\":\"데이터베이스 100%\",\"remainingAbsences\":3}]");
        assertThat(s)
                .contains("오늘은 2026-11-23(월), 지금 시각은 09:05")
                .contains("출석률(%)")
                .contains("\"title\":\"데이터베이스 100%\"")
                .doesNotContain("{TODAY}").doesNotContain("{SUMMARY}");
    }
}
