package com.ppiyaki.common.druginfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "druginfo.api", name = "service-key")
public class DrugInfoClient {

    private static final Logger log = LoggerFactory.getLogger(DrugInfoClient.class);
    private static final String API_URL = "https://apis.data.go.kr/1471000/DrbEasyDrugInfoService/getDrbEasyDrugList";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final String serviceKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedDrugInfo> cache = new ConcurrentHashMap<>();

    public DrugInfoClient(
            final DrugInfoProperties properties,
            final RestClient.Builder restClientBuilder,
            final ObjectMapper objectMapper
    ) {
        this.serviceKey = properties.serviceKey();
        this.objectMapper = objectMapper;

        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = restClientBuilder.requestFactory(factory).build();
    }

    public Optional<DrugInfoResponse> search(final String itemName) {
        final String cacheKey = itemName.strip().toLowerCase();
        final CachedDrugInfo cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.response();
        }

        log.info("DrugInfo API call: itemName={}", itemName);
        try {
            final String url = API_URL
                    + "?serviceKey=" + serviceKey
                    + "&type=json"
                    + "&itemName=" + URLEncoder.encode(itemName, StandardCharsets.UTF_8)
                    + "&numOfRows=1&pageNo=1";

            final String responseBody = restClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(String.class);

            final Optional<DrugInfoResponse> result = parseResponse(responseBody);
            cache.put(cacheKey, new CachedDrugInfo(result, Instant.now().plus(CACHE_TTL)));
            return result;

        } catch (final Exception e) {
            log.error("DrugInfo API failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<DrugInfoResponse> parseResponse(final String responseBody) {
        try {
            final JsonNode root = objectMapper.readTree(responseBody);
            final int totalCount = root.path("body").path("totalCount").asInt(0);
            if (totalCount == 0) {
                return Optional.empty();
            }

            final JsonNode items = root.path("body").path("items");
            final JsonNode item;
            if (items.isArray() && !items.isEmpty()) {
                item = items.get(0);
            } else {
                return Optional.empty();
            }

            return Optional.of(new DrugInfoResponse(
                    item.path("itemName").asText(null),
                    item.path("entpName").asText(null),
                    stripHtml(item.path("efcyQesitm").asText(null)),
                    stripHtml(item.path("useMethodQesitm").asText(null)),
                    stripHtml(item.path("atpnQesitm").asText(null)),
                    stripHtml(item.path("intrcQesitm").asText(null)),
                    stripHtml(item.path("seQesitm").asText(null)),
                    stripHtml(item.path("depositMethodQesitm").asText(null)),
                    item.path("itemImage").asText(null)
            ));
        } catch (final Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "DrugInfo parse failed: " + e.getMessage());
        }
    }

    private String stripHtml(final String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("<[^>]+>", "").strip();
    }

    private record CachedDrugInfo(
            Optional<DrugInfoResponse> response,
            Instant expiresAt
    ) {
    }
}
