package com.ppiyaki.user.service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryInviteCodeStore implements InviteCodeStore {

    private final Map<String, CachedInviteCode> store = new ConcurrentHashMap<>();

    @Override
    public void save(final String codeHash, final Long seniorId, final long ttlSeconds) {
        store.put(codeHash, new CachedInviteCode(seniorId, Instant.now().plusSeconds(ttlSeconds)));
    }

    @Override
    public Optional<Long> findSeniorIdByCodeHash(final String codeHash) {
        final CachedInviteCode cached = store.get(codeHash);
        if (cached == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(cached.expiresAt())) {
            store.remove(codeHash);
            return Optional.empty();
        }
        return Optional.of(cached.seniorId());
    }

    @Override
    public void markUsed(final String codeHash) {
        store.remove(codeHash);
    }

    private record CachedInviteCode(
            Long seniorId,
            Instant expiresAt
    ) {
    }
}
