package com.ppiyaki.infrastructure.mfds;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisMfdsResponseCache implements MfdsResponseCache {

    private static final Logger log = LoggerFactory.getLogger(RedisMfdsResponseCache.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "mfds-cache:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisMfdsResponseCache(
            final StringRedisTemplate redisTemplate,
            final ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<CachedMfdsResponse> get(final String operation, final String queryKey) {
        final String redisKey = buildKey(operation, queryKey);
        final String json = redisTemplate.opsForValue().get(redisKey);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, CachedMfdsResponse.class));
        } catch (final JsonProcessingException e) {
            log.warn("MFDS cache deserialize failed: key={}, error={}", redisKey, e.getMessage());
            redisTemplate.delete(redisKey);
            return Optional.empty();
        }
    }

    @Override
    public void put(final String operation, final String queryKey, final CachedMfdsResponse response) {
        final String redisKey = buildKey(operation, queryKey);
        try {
            final String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey, json, TTL);
        } catch (final JsonProcessingException e) {
            log.warn("MFDS cache serialize failed: key={}, error={}", redisKey, e.getMessage());
        }
    }

    @Override
    public void invalidate(final String operation, final String queryKey) {
        redisTemplate.delete(buildKey(operation, queryKey));
    }

    private String buildKey(final String operation, final String queryKey) {
        return KEY_PREFIX + operation + ":" + queryKey;
    }
}
