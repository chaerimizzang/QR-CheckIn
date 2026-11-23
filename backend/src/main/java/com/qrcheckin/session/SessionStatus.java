package com.qrcheckin.session;

/**
 * 회차 상태 전이: SCHEDULED → OPEN → CLOSED (계획서 6.3)
 * CANCELED(휴강): 출석을 시작하기 전(SCHEDULED)에만 바꿀 수 있고, [휴강 취소]로 SCHEDULED 로 돌아간다.
 * 휴강 회차는 전체 회차(F 기준)·출석·알림·출석 인정 신청에서 빠진다. 보강은 없다.
 */
public enum SessionStatus {
    SCHEDULED,
    OPEN,
    CLOSED,
    CANCELED
}
