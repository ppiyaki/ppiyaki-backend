package com.ppiyaki.common.mfds;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class InMemoryMfdsResponseCache implements MfdsResponseCache {

    private static final Duration TTL = Duration.ofHours(24);

    private final Map<String, CachedMfdsResponse> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<CachedMfdsResponse> get(final String operation, final String queryKey) {
        final String key = buildKey(operation, queryKey);
        final CachedMfdsResponse cached = cache.get(key);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached.fetchedAt().plus(TTL).isBefore(Instant.now())) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(cached);
    }

    @Override
    public void put(final String operation, final String queryKey, final CachedMfdsResponse response) {
        cache.put(buildKey(operation, queryKey), response);
    }

    @Override
    public void invalidate(final String operation, final String queryKey) {
        cache.remove(buildKey(operation, queryKey));
    }

    private String buildKey(final String operation, final String queryKey) {
        return operation + ":" + queryKey;
    }
}
