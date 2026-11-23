package com.qrcheckin.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.qrcheckin.assistant.llm.OpenAiCompatibleClientAccess;

/** 429 응답에서 다시 시도까지 남은 시간을 읽는다 */
class OpenAiCompatibleClientTest {

    @Test
    void 다시_시도_시간_읽기() {
        assertThat(OpenAiCompatibleClientAccess.retryAfter("30", null)).isEqualTo(30);
        assertThat(OpenAiCompatibleClientAccess.retryAfter(null,
                "{\"error\":{\"details\":[{\"retryDelay\": \"23s\"}]}}")).isEqualTo(23);
        assertThat(OpenAiCompatibleClientAccess.retryAfter(null, "{\"retryDelay\":\"7.5s\"}")).isEqualTo(7);
        assertThat(OpenAiCompatibleClientAccess.retryAfter(null, "no info")).isZero();
    }
}
