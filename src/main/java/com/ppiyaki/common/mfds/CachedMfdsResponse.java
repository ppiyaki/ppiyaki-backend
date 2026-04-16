package com.ppiyaki.common.mfds;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CachedMfdsResponse(
        String operation,
        String queryKey,
        Instant fetchedAt,
        int totalCount,
        List<Map<String, Object>> items
) {
}
