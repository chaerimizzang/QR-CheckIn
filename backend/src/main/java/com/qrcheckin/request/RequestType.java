package com.qrcheckin.request;

/** 출석 인정 신청 종류 */
public enum RequestType {
    /** 출석 인정 신청(지금 화면은 이것만 쓴다) — 승인되면 출석 */
    EXCUSE,
    /** 정정: 출석했는데 결석·지각으로 처리됨 → 승인되면 신청한 상태(출석·지각)로 바뀜 */
    CORRECTION
}
