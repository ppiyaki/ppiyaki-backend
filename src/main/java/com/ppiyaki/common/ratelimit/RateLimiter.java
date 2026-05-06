package com.ppiyaki.common.ratelimit;

public interface RateLimiter {

    void checkAllowed(final String key);

    void recordFailure(final String key);

    void clearFailures(final String key);
}
