package com.qrcheckin.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.qrcheckin.checkin.QrTokenService;

@Configuration
public class AppConfig {

    /** 모든 판정은 서버 시각(Asia/Seoul) 기준(계획서 4.3). 테스트에서는 고정 Clock 으로 교체한다. */
    @Bean
    public Clock clock(@Value("${app.timezone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    public QrTokenService qrTokenService(@Value("${app.qr.secret}") String secret,
                                         @Value("${app.qr.refresh-seconds}") int refreshSeconds,
                                         @Value("${app.qr.allowed-past-windows}") int allowedPastWindows) {
        return new QrTokenService(secret, refreshSeconds, allowedPastWindows);
    }
}
