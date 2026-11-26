package com.qrcheckin.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** 지시문 만들기: '%' 가 들어 있어도 예외 없이 오늘 날짜·시각·출결 요약이 들어간다 */
class AssistantPromptTest {

    @Test
    void 퍼센트가_있어도_지시문을_만든다() {
        String s = AssistantPrompt.system(LocalDateTime.of(2026, 11, 23, 9, 5),
                "[{\"courseId\":6,\"title\":\"데이터베이스 100%\",\"remainingAbsences\":3}]",
                "수업의 2/3 이상 출석해야 해요(결석이 1/3을 넘으면 F 기준)");
        assertThat(s)
                .contains("오늘은 2026-11-23(월), 지금 시각은 09:05")
                .contains("출석률(%)")
                .contains("\"title\":\"데이터베이스 100%\"")
                .contains("수업의 2/3 이상 출석해야 해요(결석이 1/3을 넘으면 F 기준)")
                .doesNotContain("{TODAY}").doesNotContain("{CALENDAR}").doesNotContain("{SUMMARY}").doesNotContain("{FAIL_RULE}");
    }

    @Test
    void 이번주_다음주_범위와_요일을_미리_계산해_넣는다() {
        // 리허설에서 LLM 이 "다음 주"를 12/2~12/8 로, 12/3(목)을 화요일로 잘못 계산했다
        String c = AssistantPrompt.calendar(LocalDate.of(2026, 11, 26)); // 목요일
        assertThat(c)
                .contains("이번 주: 2026-11-23 ~ 2026-11-29")
                .contains("다음 주: 2026-11-30 ~ 2026-12-06")
                .contains("12/1(화)").contains("12/3(목)").contains("11/30(월)");
    }
}
