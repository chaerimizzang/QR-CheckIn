package com.qrcheckin.common;

import org.springframework.http.HttpStatus;

/**
 * 모든 API 오류 코드. 출석 인증 코드는 계획서 4.4 오류 코드표와 1:1 대응한다.
 * (LOCATION_DENIED 는 브라우저에서만 판정하므로 서버 코드에는 없다.)
 */
public enum ErrorCode {

    // 공통
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없어요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했어요"),

    // 계정 (B-01)
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일이에요"),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않아요"),

    // 교육·수강생·회차 (B-02 ~ B-05)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 이메일로 가입한 사용자가 없어요"),
    NOT_A_STUDENT(HttpStatus.BAD_REQUEST, "수강생(STUDENT) 계정만 등록할 수 있어요"),
    ALREADY_ENROLLED(HttpStatus.CONFLICT, "이미 등록된 수강생이에요"),
    SESSION_NOT_EDITABLE(HttpStatus.CONFLICT, "출석을 시작한 회차는 수정·삭제할 수 없어요"),
    LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "출석 장소를 먼저 지정해 주세요"),
    SESSION_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 출석을 시작한 회차예요"),

    // 출석 인증 (B-07, B-09) — 계획서 4.4
    QR_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "올바른 출석 QR이 아니에요"),
    QR_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "QR이 만료되었어요"),
    NOT_ENROLLED(HttpStatus.FORBIDDEN, "이 교육의 수강생이 아니에요"),
    SESSION_NOT_OPEN(HttpStatus.UNPROCESSABLE_ENTITY, "아직 출석 시간이 아니에요"),
    SESSION_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "출석이 마감되었어요"),
    LOW_ACCURACY(HttpStatus.UNPROCESSABLE_ENTITY, "위치 정확도가 낮아요"),
    OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_ENTITY, "출석 장소에서 떨어져 있어요"),
    ALREADY_CHECKED(HttpStatus.CONFLICT, "이미 출석했어요");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
