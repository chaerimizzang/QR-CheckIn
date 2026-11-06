package com.qrcheckin.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.status(e.getCode().status())
                .body(ErrorResponse.of(e.getCode(), e.getMessage(), e.getDetail()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                        ErrorCode.VALIDATION_FAILED.defaultMessage(), Map.of("fields", fields)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, "요청 형식이 올바르지 않아요", Map.of()));
    }

    /** ?courseId= 누락, /sessions/abc 처럼 숫자 자리에 문자 → 400 */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadParam(Exception e) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, "요청 파라미터를 확인해 주세요",
                        Map.of("reason", e.getMessage())));
    }

    /** 없는 주소 → 404 (500 으로 새지 않도록) */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status()).body(ErrorResponse.of(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                "지원하지 않는 요청 방식이에요(" + e.getMethod() + ")", Map.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleDenied(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status()).body(ErrorResponse.of(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }
}
