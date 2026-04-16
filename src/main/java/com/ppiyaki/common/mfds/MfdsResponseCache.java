package com.ppiyaki.common.mfds;

import java.util.Optional;

public interface MfdsResponseCache {

    Optional<CachedMfdsResponse> get(String operation, String queryKey);

    void put(String operation, String queryKey, CachedMfdsResponse response);

    void invalidate(String operation, String queryKey);
}
