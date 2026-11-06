package com.qrcheckin.checkin;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 동적 QR 토큰(계획서 4.1). TOTP 와 같은 방식이라 토큰을 DB 에 저장하지 않는다.
 *
 * <pre>
 * token       = sessionId.windowIndex.signature
 * windowIndex = floor((now - openedAt) / refreshSeconds)
 * signature   = Base64Url( HMAC-SHA256( key = serverSecret + ":" + sessionSalt, msg = "sessionId.windowIndex" ) )
 * </pre>
 *
 * 현재 주기와 직전 {@code allowedPastWindows}개 주기의 토큰을 허용한다(기본 1 → 최대 약 30초).
 * Spring 에 의존하지 않는 순수 자바 클래스라 단위 테스트가 쉽다.
 */
public class QrTokenService {

    public enum Verdict { OK, INVALID, EXPIRED }

    public record ParsedToken(long sessionId, long windowIndex, String signature) { }

    private static final String HMAC = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] serverSecret;
    private final long refreshMillis;
    private final int allowedPastWindows;

    public QrTokenService(String serverSecret, int refreshSeconds, int allowedPastWindows) {
        if (serverSecret == null || serverSecret.length() < 32) {
            throw new IllegalArgumentException("QR secret 은 32자 이상이어야 합니다");
        }
        if (refreshSeconds <= 0 || allowedPastWindows < 0) {
            throw new IllegalArgumentException("refreshSeconds > 0, allowedPastWindows >= 0");
        }
        this.serverSecret = serverSecret.getBytes(StandardCharsets.UTF_8);
        this.refreshMillis = refreshSeconds * 1000L;
        this.allowedPastWindows = allowedPastWindows;
    }

    /** 회차를 열 때마다 새로 만드는 salt */
    public static String newSalt() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public long windowIndex(Instant openedAt, Instant now) {
        long elapsed = Duration.between(openedAt, now).toMillis();
        return Math.floorDiv(elapsed, refreshMillis);
    }

    /** 현재 창이 끝나기까지 남은 시간(ms) — 관리자 화면이 다음 QR 을 언제 받아야 하는지 알려 준다 */
    public long millisUntilNextWindow(Instant openedAt, Instant now) {
        long elapsed = Duration.between(openedAt, now).toMillis();
        return refreshMillis - Math.floorMod(elapsed, refreshMillis);
    }

    public String issue(long sessionId, String salt, Instant openedAt, Instant now) {
        long window = windowIndex(openedAt, now);
        return sessionId + "." + window + "." + sign(sessionId, window, salt);
    }

    /** 형식만 검사해 sessionId 를 꺼낸다. 형식이 틀리면 empty → QR_INVALID */
    public Optional<ParsedToken> parse(String token) {
        if (token == null || token.length() > 200) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || parts[2].isEmpty()) {
            return Optional.empty();
        }
        try {
            long sessionId = Long.parseLong(parts[0]);
            long window = Long.parseLong(parts[1]);
            if (sessionId <= 0 || window < 0) {
                return Optional.empty();
            }
            return Optional.of(new ParsedToken(sessionId, window, parts[2]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * 서명을 먼저 확인하고(위조 → INVALID), 그다음 시간 창을 확인한다(오래됨 → EXPIRED).
     * 미래 창의 토큰은 서버가 발급한 적이 없으므로 INVALID.
     */
    public Verdict verify(ParsedToken token, String salt, Instant openedAt, Instant now) {
        String expected = sign(token.sessionId(), token.windowIndex(), salt);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                token.signature().getBytes(StandardCharsets.US_ASCII))) {
            return Verdict.INVALID;
        }
        long current = windowIndex(openedAt, now);
        if (token.windowIndex() > current) {
            return Verdict.INVALID;
        }
        if (current - token.windowIndex() > allowedPastWindows) {
            return Verdict.EXPIRED;
        }
        return Verdict.OK;
    }

    private String sign(long sessionId, long window, String salt) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            byte[] key = concat(serverSecret, (":" + salt).getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(key, HMAC));
            byte[] sig = mac.doFinal((sessionId + "." + window).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
