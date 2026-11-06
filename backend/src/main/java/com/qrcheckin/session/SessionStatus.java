package com.qrcheckin.session;

/** 회차 상태 전이: SCHEDULED → OPEN → CLOSED (계획서 6.3) */
public enum SessionStatus {
    SCHEDULED,
    OPEN,
    CLOSED
}
