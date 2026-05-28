package com.ppiyaki.common.ratelimit;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class InMemoryRateLimiter implements RateLimiter {

    private static final int MAX_ATTEMPTS_PER_MINUTE = 10;
    private static final long WINDOW_MINUTES = 1L;

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<LocalDateTime>> attempts = new ConcurrentHashMap<>();

    @Override
    public void checkAllowed(final String key) {
        attempts.computeIfPresent(key, (k, timestamps) -> {
            pruneOldEntries(timestamps);
            if (timestamps.size() >= MAX_ATTEMPTS_PER_MINUTE) {
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
            }
            return timestamps.isEmpty() ? null : timestamps;
        });
    }

    @Override
    public void recordFailure(final String key) {
        attempts.compute(key, (k, timestamps) -> {
            final ConcurrentLinkedDeque<LocalDateTime> deque = timestamps != null ? timestamps
                    : new ConcurrentLinkedDeque<>();
            deque.addLast(LocalDateTime.now());
            pruneOldEntries(deque);
            return deque;
        });
    }

    @Override
    public void clearFailures(final String key) {
        attempts.remove(key);
    }

    private void pruneOldEntries(final ConcurrentLinkedDeque<LocalDateTime> timestamps) {
        final LocalDateTime windowStart = LocalDateTime.now().minusMinutes(WINDOW_MINUTES);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }
    }
}
