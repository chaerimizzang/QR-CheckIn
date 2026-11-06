package com.qrcheckin.checkin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.qrcheckin.checkin.QrTokenService.Verdict;

/** 계획서 11.1 핵심 테스트 케이스 중 토큰 관련 항목 */
class QrTokenServiceTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789";
    private final QrTokenService service = new QrTokenService(SECRET, 15, 1);
    private final Instant opened = Instant.parse("2026-11-10T04:00:00Z");
    private final String salt = "salt-A";

    private Verdict verifyAt(String token, Instant now) {
        return service.verify(service.parse(token).orElseThrow(), salt, opened, now);
    }

    @Test
    void 현재_주기_토큰은_성공() {
        String token = service.issue(7, salt, opened, opened.plusSeconds(20));
        assertThat(verifyAt(token, opened.plusSeconds(25))).isEqualTo(Verdict.OK);
    }

    @Test
    void 직전_주기_토큰은_성공() {
        String token = service.issue(7, salt, opened, opened.plusSeconds(14)); // window 0
        assertThat(verifyAt(token, opened.plusSeconds(29))).isEqualTo(Verdict.OK); // window 1
    }

    @Test
    void 두_주기_전_토큰은_QR_EXPIRED() {
        String token = service.issue(7, salt, opened, opened.plusSeconds(1)); // window 0
        assertThat(verifyAt(token, opened.plusSeconds(30))).isEqualTo(Verdict.EXPIRED); // window 2
    }

    @Test
    void 다른_회차_salt_로_검증하면_QR_INVALID() {
        String token = service.issue(7, "other-salt", opened, opened.plusSeconds(1));
        assertThat(verifyAt(token, opened.plusSeconds(2))).isEqualTo(Verdict.INVALID);
    }

    @Test
    void sessionId_를_바꾼_위조_토큰은_QR_INVALID() {
        String token = service.issue(7, salt, opened, opened.plusSeconds(1));
        String forged = "8" + token.substring(token.indexOf('.'));
        assertThat(verifyAt(forged, opened.plusSeconds(2))).isEqualTo(Verdict.INVALID);
    }

    @Test
    void 미래_주기_토큰은_QR_INVALID() {
        String token = service.issue(7, salt, opened, opened.plusSeconds(60));
        assertThat(verifyAt(token, opened.plusSeconds(1))).isEqualTo(Verdict.INVALID);
    }

    @Test
    void 형식이_틀린_토큰은_parse_실패() {
        assertThat(service.parse("abc")).isEmpty();
        assertThat(service.parse("1.2")).isEmpty();
        assertThat(service.parse("x.0.sig")).isEmpty();
        assertThat(service.parse("1.-1.sig")).isEmpty();
        assertThat(service.parse(null)).isEmpty();
    }

    @Test
    void 다음_QR까지_남은_시간() {
        assertThat(service.millisUntilNextWindow(opened, opened.plusSeconds(10))).isEqualTo(5_000);
        assertThat(service.millisUntilNextWindow(opened, opened.plusSeconds(15))).isEqualTo(15_000);
    }
}
