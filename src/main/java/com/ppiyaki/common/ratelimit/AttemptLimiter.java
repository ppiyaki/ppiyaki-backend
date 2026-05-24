package com.ppiyaki.common.ratelimit;

public interface AttemptLimiter {

    void checkAllowed(final String key);

    void recordAttempt(final String key);

    void clear(final String key);
}
