package com.ppiyaki.infrastructure.druginfo;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDrugInfoCache implements DrugInfoCache {

    private static final Duration TTL = Duration.ofHours(24);

    private final Map<String, CachedDrugInfo> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<DrugInfoResponse> get(final String itemName) {
        final String key = itemName.strip().toLowerCase();
        final CachedDrugInfo cached = cache.get(key);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAt().isBefore(Instant.now())) {
            cache.remove(key);
            return null;
        }
        return cached.response();
    }

    @Override
    public void put(final String itemName, final Optional<DrugInfoResponse> response) {
        final String key = itemName.strip().toLowerCase();
        cache.put(key, new CachedDrugInfo(response, Instant.now().plus(TTL)));
    }

    private record CachedDrugInfo(
            Optional<DrugInfoResponse> response,
            Instant expiresAt
    ) {
    }
}
