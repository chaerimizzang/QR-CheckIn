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

    public LlmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LlmException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
