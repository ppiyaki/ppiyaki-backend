package com.ppiyaki.infrastructure.druginfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisDrugInfoCache implements DrugInfoCache {

    private static final Logger log = LoggerFactory.getLogger(RedisDrugInfoCache.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "drug-info:";
    private static final String EMPTY_MARKER = "__EMPTY__";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisDrugInfoCache(
            final StringRedisTemplate redisTemplate,
            final ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<DrugInfoResponse> get(final String itemName) {
        final String redisKey = buildKey(itemName);
        final String json = redisTemplate.opsForValue().get(redisKey);
        if (json == null) {
            return null;
        }
        if (EMPTY_MARKER.equals(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, DrugInfoResponse.class));
        } catch (final JsonProcessingException e) {
            log.warn("DrugInfo cache deserialize failed: key={}, error={}", redisKey, e.getMessage());
            redisTemplate.delete(redisKey);
            return null;
        }
    }

    @Override
    public void put(final String itemName, final Optional<DrugInfoResponse> response) {
        final String redisKey = buildKey(itemName);
        try {
            final String json = response.isPresent()
                    ? objectMapper.writeValueAsString(response.get())
                    : EMPTY_MARKER;
            redisTemplate.opsForValue().set(redisKey, json, TTL);
        } catch (final JsonProcessingException e) {
            log.warn("DrugInfo cache serialize failed: key={}, error={}", redisKey, e.getMessage());
        }
    }

    private String buildKey(final String itemName) {
        return KEY_PREFIX + itemName.strip().toLowerCase();
    }
}
