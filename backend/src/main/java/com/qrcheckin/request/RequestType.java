package com.qrcheckin.request;

/** 출석 인정 신청 종류 */
public enum RequestType {
    /** 공결: 병결·예비군·경조사처럼 사유가 있는 결석 → 승인되면 EXCUSED(출석률에서 제외) */
    EXCUSE,
    /** 정정: 출석했는데 결석·지각으로 처리됨 → 승인되면 신청한 상태(출석·지각)로 바뀜 */
    CORRECTION
}
