package com.qrcheckin.request;

public enum RequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** 학생이 처리 전에 취소 */
    CANCELED
}
