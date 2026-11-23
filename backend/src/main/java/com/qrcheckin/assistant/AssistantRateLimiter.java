package com.qrcheckin.assistant;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;

/**
 * 학생별 질문 수 제한(분당·하루). 무료 한도를 한 사람이 다 쓰지 않게 막는다.
 * 서버 메모리에만 두므로 서버를 다시 켜면 초기화된다(수업 프로젝트 규모에서는 충분).
 */
@Component
class AssistantRateLimiter {

    private final Clock clock;
    private final int perMinute;
    private final int perDay;
    private final Map<Long, Usage> usage = new ConcurrentHashMap<>();

    AssistantRateLimiter(Clock clock,
                         @Value("${app.assistant.per-minute-limit:6}") int perMinute,
                         @Value("${app.assistant.daily-limit:40}") int perDay) {
        this.clock = clock;
        this.perMinute = perMinute;
        this.perDay = perDay;
    }

    /** 한 번 쓴다. 넘으면 429 */
    void acquire(Long userId) {
        Usage u = usage.computeIfAbsent(userId, k -> new Usage());
        synchronized (u) {
            Instant now = clock.instant();
            LocalDate today = LocalDate.now(clock);
            if (!today.equals(u.day)) {
                u.day = today;
                u.dayCount = 0;
            }
            while (!u.recent.isEmpty() && u.recent.peekFirst().isBefore(now.minusSeconds(60))) {
                u.recent.pollFirst();
            }
            if (u.dayCount >= perDay) {
                throw new ApiException(ErrorCode.ASSISTANT_RATE_LIMITED,
                        "오늘 질문 " + perDay + "개를 다 썼어요. 내일 다시 물어봐 주세요");
            }
            if (u.recent.size() >= perMinute) {
                throw new ApiException(ErrorCode.ASSISTANT_RATE_LIMITED);
            }
            u.recent.addLast(now);
            u.dayCount++;
        }
    }

    private static final class Usage {
        private final Deque<Instant> recent = new ArrayDeque<>();
        private LocalDate day;
        private int dayCount;
    }
}
