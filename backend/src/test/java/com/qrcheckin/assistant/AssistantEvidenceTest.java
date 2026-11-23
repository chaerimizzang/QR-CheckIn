package com.qrcheckin.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** 답변 숫자 대조: 날짜·시각·반올림한 비율은 통과, 도구 결과에 없는 횟수·비율은 잡는다 */
class AssistantEvidenceTest {

    private AssistantEvidence evidence() {
        AssistantEvidence ev = new AssistantEvidence();
        ev.allowDate(LocalDateTime.of(2026, 11, 23, 15, 31));
        ev.allowFrom("{\"attendanceRate\":66.66666666666667,\"convertedAbsences\":1,\"remainingAbsences\":0,"
                + "\"failRatio\":0.25,\"description\":\"25%를 넘으면\"}");
        return ev;
    }

    @Test
    void 날짜와_시각은_대조하지_않는다() {
        assertThat(evidence().unknownNumbers("네, 내일 11월 24일(화) 오후 1시 30분에 데이터베이스 수업이 있어요.")).isEmpty();
        assertThat(evidence().unknownNumbers("11/24(화) 13:00~14:30, 2026-11-24 수업이에요.")).isEmpty();
    }

    @Test
    void 반올림한_비율과_규칙_숫자는_통과() {
        AssistantEvidence ev = evidence();
        assertThat(ev.unknownNumbers("출석률은 66.7%이고 인정 결석 1회예요.")).isEmpty();
        assertThat(ev.unknownNumbers("출석률은 약 67%예요.")).isEmpty();
        assertThat(ev.unknownNumbers("인정 결석이 전체의 1/4, 즉 25%를 넘으면 F 기준이에요.")).isEmpty();
    }

    @Test
    void 도구_결과에_없는_횟수와_비율은_잡는다() {
        AssistantEvidence ev = evidence();
        assertThat(ev.unknownNumbers("자료구조 결석은 77회예요.")).containsExactly("77");
        assertThat(ev.unknownNumbers("출석률은 72.5%예요.")).containsExactly("72.5");
        assertThat(ev.unknownNumbers("남은 결석은 2회예요.")).containsExactly("2");
    }
}
