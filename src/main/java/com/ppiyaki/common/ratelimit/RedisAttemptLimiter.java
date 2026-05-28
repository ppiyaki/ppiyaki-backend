package com.ppiyaki.common.ratelimit;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisAttemptLimiter implements AttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisAttemptLimiter.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "attempt-limit:";

    private final StringRedisTemplate redisTemplate;

    public RedisAttemptLimiter(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void checkAllowed(final String key) {
        final String redisKey = KEY_PREFIX + key;
        final String value = redisTemplate.opsForValue().get(redisKey);
        if (value != null) {
            try {
                if (Integer.parseInt(value) >= MAX_ATTEMPTS) {
                    throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
                }
            } catch (final NumberFormatException e) {
                log.warn("Attempt limit key has invalid value: key={}, value={}", redisKey, value);
                redisTemplate.delete(redisKey);
            }
        }
    }

    @Override
    public void recordAttempt(final String key) {
        final String redisKey = KEY_PREFIX + key;
        final Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, TTL);
        }
    }

    @Override
    public void clear(final String key) {
        final String redisKey = KEY_PREFIX + key;
        redisTemplate.delete(redisKey);
    }
}
