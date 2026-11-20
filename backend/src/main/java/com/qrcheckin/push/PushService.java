package com.qrcheckin.push;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;
import com.qrcheckin.user.User;
import com.qrcheckin.user.UserRepository;

/** 웹 푸시: 구독 저장·삭제와 알림 보내기 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final PushSubscriptionRepository subscriptions;
    private final UserRepository userRepository;
    private final VapidKeys vapidKeys;
    private final PushTransport transport;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String subject;

    public PushService(PushSubscriptionRepository subscriptions, UserRepository userRepository, VapidKeys vapidKeys,
                       PushTransport transport, ObjectMapper objectMapper, Clock clock,
                       @Value("${app.push.subject:mailto:qr-checkin@example.com}") String subject) {
        this.subscriptions = subscriptions;
        this.userRepository = userRepository;
        this.vapidKeys = vapidKeys;
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.subject = subject;
    }

    public String publicKey() {
        return vapidKeys.get().publicKeyBase64();
    }

    /** 이 기기 구독 저장. 같은 기기(endpoint)가 이미 있으면 지금 로그인한 사람 것으로 바꾼다 */
    @Transactional
    public void subscribe(Long userId, String endpoint, String p256dh, String auth) {
        validate(endpoint, p256dh, auth);
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        subscriptions.findByEndpoint(endpoint).ifPresentOrElse(
                s -> s.reassign(user, p256dh, auth),
                () -> subscriptions.save(new PushSubscription(user, endpoint, p256dh, auth, LocalDateTime.now(clock))));
    }

    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        subscriptions.deleteByEndpointAndUserId(endpoint, userId);
    }

    /** 알림을 보낸다. 브라우저가 구독을 끊은 경우(404·410) 구독을 지운다. @return 성공한 기기 수 */
    public int send(Collection<Long> userIds, PushMessage message) {
        if (userIds.isEmpty()) {
            return 0;
        }
        List<PushSubscription> targets = subscriptions.findByUserIdIn(userIds);
        if (targets.isEmpty()) {
            return 0;
        }
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        VapidKeys.Keys keys = vapidKeys.get();
        long exp = clock.instant().getEpochSecond() + 12 * 3600;
        int ok = 0;
        for (PushSubscription s : targets) {
            byte[] body;
            try {
                body = WebPushCrypto.encrypt(payload, WebPushCrypto.unb64(s.getP256dh()), WebPushCrypto.unb64(s.getAuth()));
            } catch (RuntimeException e) {
                log.info("잘못된 구독 키라 삭제: {}", e.getMessage());
                subscriptions.deleteByEndpoint(s.getEndpoint());
                continue;
            }
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("TTL", "600"); // 10분 안에 못 전하면 버린다(이미 지난 수업 알림은 의미 없음)
            headers.put("Urgency", "high");
            headers.put("Content-Type", "application/octet-stream");
            headers.put("Content-Encoding", "aes128gcm");
            headers.put("Authorization",
                    WebPushCrypto.vapidAuthorization(origin(s.getEndpoint()), subject, exp, keys.publicKey(), keys.privateKey()));
            int status = transport.post(s.getEndpoint(), headers, body);
            if (status >= 200 && status < 300) {
                ok++;
            } else if (status == 404 || status == 410) {
                subscriptions.deleteByEndpoint(s.getEndpoint()); // 브라우저에서 알림을 끄거나 앱을 지움
            }
        }
        return ok;
    }

    private static String origin(String endpoint) {
        URI u = URI.create(endpoint);
        return u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
    }

    private static void validate(String endpoint, String p256dh, String auth) {
        try {
            URI u = URI.create(endpoint);
            if (!"https".equals(u.getScheme()) || u.getHost() == null) {
                throw new IllegalArgumentException();
            }
            WebPushCrypto.decodePublic(WebPushCrypto.unb64(p256dh));
            if (WebPushCrypto.unb64(auth).length != 16) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "알림 구독 정보가 올바르지 않아요");
        }
    }
}
