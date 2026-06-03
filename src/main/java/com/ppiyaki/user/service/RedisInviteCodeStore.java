package com.ppiyaki.user.service;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisInviteCodeStore implements InviteCodeStore {

    private static final Logger log = LoggerFactory.getLogger(RedisInviteCodeStore.class);
    private static final String KEY_PREFIX = "invite-code:";

    private final StringRedisTemplate redisTemplate;

    public RedisInviteCodeStore(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(final String codeHash, final Long seniorId, final long ttlSeconds) {
        final String redisKey = KEY_PREFIX + codeHash;
        redisTemplate.opsForValue().set(redisKey, String.valueOf(seniorId), Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<Long> consume(final String codeHash) {
        final String redisKey = KEY_PREFIX + codeHash;
        final String value = redisTemplate.opsForValue().getAndDelete(redisKey);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (final NumberFormatException e) {
            log.warn("Invite code key has invalid value: key={}, value={}", redisKey, value);
            return Optional.empty();
        }
    }
}
