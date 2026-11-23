package com.qrcheckin.session;

import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class SessionDtos {

    private SessionDtos() {
    }

    /**
     * B-03 회차 등록 + B-05 출석 조건.
     * 장소(latitude/longitude)는 관리자 휴대폰의 [현재 위치로 지정] 값이며, 나중에 지정해도 된다(출석 시작 전까지).
     * radiusM, checkinMinutes 를 비우면 기본값(50m, 5분)을 쓴다.
     */
    public record SessionRequest(
            @NotNull LocalDateTime scheduledAt,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @Min(10) @Max(1000) Integer radiusM,
            @Min(1) @Max(60) Integer checkinMinutes) {
    }

    public record SessionResponse(Long id, Long courseId, int sessionNo, LocalDateTime scheduledAt,
                                  Double latitude, Double longitude, int radiusM, int checkinMinutes,
                                  SessionStatus status, LocalDateTime openedAt, LocalDateTime closedAt,
                                  /** 휴강 사유(휴강이 아니거나 사유가 없으면 null) */
                                  String cancelReason) {
        public static SessionResponse from(ClassSession s) {
            return new SessionResponse(s.getId(), s.getCourse().getId(), s.getSessionNo(), s.getScheduledAt(),
                    s.getLatitude(), s.getLongitude(), s.getRadiusM(), s.getCheckinMinutes(),
                    s.getStatus(), s.getOpenedAt(), s.getClosedAt(), s.getCancelReason());
        }
    }

    /** 휴강 요청: 사유는 선택(학생 알림·화면에 함께 표시) */
    public record CancelRequest(
            @jakarta.validation.constraints.Size(max = 200, message = "휴강 사유는 200자까지 쓸 수 있어요") String reason) {
    }
}
