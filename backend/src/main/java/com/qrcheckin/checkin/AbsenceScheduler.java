package com.qrcheckin.checkin;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** B-08 자동 결석: QR 출석 시간이 끝난 회차를 주기적으로 마감한다(기본 1분) */
@Component
public class AbsenceScheduler {

    private final SessionRunService sessionRunService;

    public AbsenceScheduler(SessionRunService sessionRunService) {
        this.sessionRunService = sessionRunService;
    }

    @Scheduled(fixedDelayString = "${app.checkin.close-check-ms}")
    public void closeExpired() {
        sessionRunService.closeExpiredSessions();
    }
}
