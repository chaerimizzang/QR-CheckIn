package com.qrcheckin.assistant.llm;

/** 테스트에서 패키지 내부 메서드를 부르기 위한 통로 */
public final class OpenAiCompatibleClientAccess {

    private OpenAiCompatibleClientAccess() {
    }

    public static int retryAfter(String header, String body) {
        return OpenAiCompatibleClient.retryAfterSeconds(header, body);
    }
}
