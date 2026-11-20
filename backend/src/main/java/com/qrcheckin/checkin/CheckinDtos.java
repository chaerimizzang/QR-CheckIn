package com.qrcheckin.checkin;

import java.time.LocalDateTime;

import com.qrcheckin.attendance.AttendanceStatus;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class CheckinDtos {

    private CheckinDtos() {
    }

    /** B-06 관리자 QR 화면이 15초마다 받아 가는 값 */
    public record QrResponse(
            String token,
            /** QR 에 그대로 담을 출석 URL — 기본 카메라 앱으로 찍어도 출석 페이지가 열린다 */
            String url,
            int refreshSeconds,
            /** 다음 QR 로 바뀌기까지 남은 시간(ms). 프론트는 이 시간 뒤에 다시 요청한다 */
            long nextRefreshMs,
            LocalDateTime closedAt,
            long remainingSeconds,
            long presentCount,
            long enrolledCount) {
    }

    /** B-07 학생 출석 요청: QR 토큰 + 브라우저 Geolocation 값 */
    public record CheckinRequest(
            @NotBlank @Size(max = 200) String token,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @PositiveOrZero Double accuracy,
            /** D-12 기기 번호: 브라우저가 처음 한 번 만들어 저장해 두는 임의 값 */
            @NotBlank(message = "기기 정보가 없어요. 페이지를 새로고침해 주세요")
            @Pattern(regexp = "[A-Za-z0-9-]{8,64}", message = "기기 정보 형식이 올바르지 않아요")
            String deviceId) {
    }

    public record CheckinResponse(
            AttendanceStatus status,
            LocalDateTime checkedAt,
            Long courseId,
            String courseTitle,
            int sessionNo) {
    }
}
