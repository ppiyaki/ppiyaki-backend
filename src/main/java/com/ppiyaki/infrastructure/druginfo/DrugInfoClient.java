package com.ppiyaki.infrastructure.druginfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.infrastructure.mfds.MfdsApiProperties;
import com.ppiyaki.infrastructure.observability.ExternalApiMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class DrugInfoClient {

    private static final Logger log = LoggerFactory.getLogger(DrugInfoClient.class);
    private static final String API_URL = "https://apis.data.go.kr/1471000/DrbEasyDrugInfoService/getDrbEasyDrugList";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private static final String API = "drug_info";
    private static final String OPERATION = "search";

    private final String serviceKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedDrugInfo> cache = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public DrugInfoClient(
            final MfdsApiProperties properties,
            final RestClient.Builder restClientBuilder,
            final ObjectMapper objectMapper,
            final MeterRegistry meterRegistry
    ) {
        this.serviceKey = properties.serviceKey();
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;

        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = restClientBuilder.requestFactory(factory).build();

        this.cacheHitCounter = Counter.builder("ppiyaki.cache.hits")
                .tag("cache", "drug_info")
                .description("DrugInfo API 응답 캐시 hit")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("ppiyaki.cache.misses")
                .tag("cache", "drug_info")
                .description("DrugInfo API 응답 캐시 miss (외부 API 호출 발생)")
                .register(meterRegistry);
    }

    public Optional<DrugInfoResponse> search(final String itemName) {
        final String cacheKey = itemName.strip().toLowerCase();
        final CachedDrugInfo cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            cacheHitCounter.increment();
            return cached.response();
        }

        cacheMissCounter.increment();
        log.info("DrugInfo API call: itemName={}", itemName);
        final Timer.Sample sample = Timer.start(meterRegistry);
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
            ExternalApiMetrics.record(meterRegistry, API, OPERATION, ExternalApiMetrics.RESULT_SUCCESS, sample);
            return result;

        } catch (final Exception e) {
            ExternalApiMetrics.record(meterRegistry, API, OPERATION, ExternalApiMetrics.RESULT_FAILED, sample);
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
