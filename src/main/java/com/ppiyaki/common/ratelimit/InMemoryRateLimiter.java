package com.ppiyaki.common.ratelimit;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRateLimiter implements RateLimiter {

    private static final int MAX_ATTEMPTS_PER_MINUTE = 10;
    private static final long WINDOW_MINUTES = 1L;

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<LocalDateTime>> attempts = new ConcurrentHashMap<>();

    @Override
    public void checkAllowed(final String key) {
        final ConcurrentLinkedDeque<LocalDateTime> timestamps = attempts.get(key);
        if (timestamps == null) {
            return;
        }

        final LocalDateTime windowStart = LocalDateTime.now().minusMinutes(WINDOW_MINUTES);
        final long recentAttempts = timestamps.stream()
                .filter(t -> t.isAfter(windowStart))
                .count();

        if (recentAttempts >= MAX_ATTEMPTS_PER_MINUTE) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    @Override
    public void recordFailure(final String key) {
        attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>())
                .addLast(LocalDateTime.now());
        cleanupOldEntries(key);
    }

    @Override
    public void clearFailures(final String key) {
        attempts.remove(key);
    }

    private void cleanupOldEntries(final String key) {
        final ConcurrentLinkedDeque<LocalDateTime> timestamps = attempts.get(key);
        if (timestamps == null) {
            return;
        }
        final LocalDateTime windowStart = LocalDateTime.now().minusMinutes(WINDOW_MINUTES);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }
    }
}
