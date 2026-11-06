package com.qrcheckin.common;

import java.util.Map;

/** 서비스 계층에서 던지는 예외. GlobalExceptionHandler 가 {code, message, detail} 로 변환한다. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> detail;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), Map.of());
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> detail) {
        super(message);
        this.code = code;
        this.detail = detail;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }
}
