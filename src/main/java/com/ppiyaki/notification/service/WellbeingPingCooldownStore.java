package com.ppiyaki.notification.service;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class WellbeingPingCooldownStore {

    private static final String KEY_PREFIX = "wellbeing-ping:cooldown:";
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public WellbeingPingCooldownStore(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(final long seniorId, final long caregiverId) {
        final String key = buildKey(seniorId, caregiverId);
        final Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", COOLDOWN);
        return Boolean.TRUE.equals(acquired);
    }

    public Optional<Long> getRetryAfterSeconds(final long seniorId, final long caregiverId) {
        final String key = buildKey(seniorId, caregiverId);
        final Long ttlSeconds = redisTemplate.getExpire(key);
        if (ttlSeconds == null || ttlSeconds <= 0L) {
            return Optional.empty();
        }
        return Optional.of(ttlSeconds);
    }

    private String buildKey(final long seniorId, final long caregiverId) {
        return KEY_PREFIX + seniorId + ":" + caregiverId;
    }
}
