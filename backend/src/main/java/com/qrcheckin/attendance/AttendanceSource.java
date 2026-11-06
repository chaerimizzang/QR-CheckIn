package com.qrcheckin.attendance;

/** 출결 기록을 만든 주체. MANUAL 기록은 스케줄러가 덮어쓰지 않는다. */
public enum AttendanceSource {
    QR,
    SYSTEM,
    MANUAL
}
