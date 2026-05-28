package com.ppiyaki.user.service;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisInviteCodeStore implements InviteCodeStore {

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
    public Optional<Long> findSeniorIdByCodeHash(final String codeHash) {
        final String redisKey = KEY_PREFIX + codeHash;
        final String value = redisTemplate.opsForValue().get(redisKey);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

    @Override
    public void markUsed(final String codeHash) {
        final String redisKey = KEY_PREFIX + codeHash;
        redisTemplate.delete(redisKey);
    }
}
