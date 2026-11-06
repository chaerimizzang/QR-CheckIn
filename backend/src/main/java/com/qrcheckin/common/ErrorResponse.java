package com.qrcheckin.common;

import java.util.Map;

/** 모든 오류 응답의 공통 형식 (계획서 7장). */
public record ErrorResponse(String code, String message, Map<String, Object> detail) {

    public static ErrorResponse of(ErrorCode code) {
        return new ErrorResponse(code.name(), code.defaultMessage(), Map.of());
    }

    public static ErrorResponse of(ErrorCode code, String message, Map<String, Object> detail) {
        return new ErrorResponse(code.name(), message, detail == null ? Map.of() : detail);
    }
}
