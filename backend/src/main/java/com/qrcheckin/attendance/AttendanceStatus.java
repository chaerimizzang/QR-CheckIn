package com.qrcheckin.attendance;

/** 출결 상태(계획서 3.3). LATE·EXCUSED 는 관리자 수정(또는 출석 인정 승인)으로만 부여한다. */
public enum AttendanceStatus {
    PRESENT,
    LATE,
    ABSENT,
    /** 공결(출석 인정) — 출석률 계산에서 이 회차를 빼 준다 */
    EXCUSED
}
