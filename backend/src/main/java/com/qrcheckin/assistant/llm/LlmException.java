package com.qrcheckin.assistant.llm;

/** LLM 호출 실패. 메시지에는 API 키를 넣지 않는다 */
public class LlmException extends Exception {

    public enum Kind {
        /** 주소·모델·키 중 비어 있는 값이 있음 */
        NOT_CONFIGURED,
        /** 무료 한도 초과(429) */
        RATE_LIMITED,
        /** 시간 초과·네트워크(학교 와이파이 HTTPS 가로채기 포함) */
        NETWORK,
        /** 그 밖의 오류(키·모델 이름 틀림, 응답 형식 이상 등) */
        FAILED
    }

    private final Kind kind;
    /** 429 일 때: 하루 한도인지(아니면 분당 한도) */
    private final boolean daily;
    /** 429 일 때: 공급자가 알려 준 다시 시도까지 남은 초(모르면 0) */
    private final int retryAfterSeconds;

    public LlmException(Kind kind, String message) {
        this(kind, message, null);
    }

    public LlmException(Kind kind, String message, Throwable cause) {
        this(kind, message, cause, false, 0);
    }

    public LlmException(Kind kind, String message, Throwable cause, boolean daily, int retryAfterSeconds) {
        super(message, cause);
        this.kind = kind;
        this.daily = daily;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public boolean daily() {
        return daily;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Kind kind() {
        return kind;
    }
}
