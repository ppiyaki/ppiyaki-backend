package com.ppiyaki.common.ratelimit;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

public class RedisRateLimiter implements RateLimiter {

    private static final int MAX_ATTEMPTS_PER_MINUTE = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY_PREFIX = "rate-limit:";

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void checkAllowed(final String key) {
        final String redisKey = KEY_PREFIX + key;
        final long windowStart = Instant.now().minus(WINDOW).toEpochMilli();

        pruneOldEntries(redisKey, windowStart);

        final Long count = redisTemplate.opsForZSet().zCard(redisKey);
        if (count != null && count >= MAX_ATTEMPTS_PER_MINUTE) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    @Override
    public void recordFailure(final String key) {
        final String redisKey = KEY_PREFIX + key;
        final long now = Instant.now().toEpochMilli();

        pruneOldEntries(redisKey, now - WINDOW.toMillis());

        final ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(redisKey, UUID.randomUUID().toString(), now);
        redisTemplate.expire(redisKey, WINDOW);
    }

    @Override
    public void clearFailures(final String key) {
        final String redisKey = KEY_PREFIX + key;
        redisTemplate.delete(redisKey);
    }

    private void pruneOldEntries(final String redisKey, final long windowStart) {
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);
    }
}
