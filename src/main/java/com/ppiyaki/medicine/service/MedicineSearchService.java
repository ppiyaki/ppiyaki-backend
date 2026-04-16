package com.ppiyaki.medicine.service;

import com.ppiyaki.common.mfds.CachedMfdsResponse;
import com.ppiyaki.common.mfds.MfdsApiClient;
import com.ppiyaki.medicine.controller.dto.MedicineCandidate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class MedicineSearchService {

    private static final String OPERATION = "getDurPrdlstInfoList03";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final MfdsApiClient mfdsApiClient;

    public MedicineSearchService(final MfdsApiClient mfdsApiClient) {
        this.mfdsApiClient = mfdsApiClient;
    }

    public List<MedicineCandidate> search(final String query, final int limit) {
        final int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        final Map<String, String> params = new LinkedHashMap<>();
        params.put("itemName", query);
        params.put("numOfRows", String.valueOf(effectiveLimit));
        params.put("pageNo", "1");

        final String cacheKey = "search:" + query.strip().toLowerCase();
        final CachedMfdsResponse response = mfdsApiClient.call(OPERATION, params, cacheKey);

        return response.items().stream()
                .map(MedicineCandidate::fromMfdsItem)
                .limit(effectiveLimit)
                .toList();
    }

    public List<MedicineCandidate> search(final String query) {
        return search(query, DEFAULT_LIMIT);
    }
}
