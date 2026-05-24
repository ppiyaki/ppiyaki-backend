package com.ppiyaki.common.ratelimit;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryAttemptLimiter implements AttemptLimiter {

    private static final int MAX_ATTEMPTS = 5;

    private final ConcurrentHashMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

    @Override
    public void checkAllowed(final String key) {
        final AtomicInteger count = attempts.get(key);
        if (count != null && count.get() >= MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    @Override
    public void recordAttempt(final String key) {
        attempts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    @Override
    public void clear(final String key) {
        attempts.remove(key);
    }
}
