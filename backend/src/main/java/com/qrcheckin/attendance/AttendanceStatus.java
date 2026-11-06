package com.qrcheckin.attendance;

/** 출결 상태(계획서 3.3). LATE 는 관리자 수정으로만 부여한다. */
public enum AttendanceStatus {
    PRESENT,
    LATE,
    ABSENT
}
