package com.ppiyaki.infrastructure.mfds;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class MfdsApiClient {

    private static final Logger log = LoggerFactory.getLogger(MfdsApiClient.class);
    private static final String RESULT_CODE_SUCCESS = "00";

    private final MfdsApiProperties properties;
    private final MfdsResponseCache cache;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public MfdsApiClient(
            final MfdsApiProperties properties,
            final MfdsResponseCache cache,
            final RestClient.Builder restClientBuilder,
            final ObjectMapper objectMapper,
            final MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.cache = cache;
        this.objectMapper = objectMapper;

        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeout()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeout()));

        this.restClient = restClientBuilder
                .requestFactory(factory)
                .build();

        this.cacheHitCounter = Counter.builder("ppiyaki.cache.hits")
                .tag("cache", "mfds")
                .description("MFDS API 응답 캐시 hit")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("ppiyaki.cache.misses")
                .tag("cache", "mfds")
                .description("MFDS API 응답 캐시 miss (외부 API 호출 발생)")
                .register(meterRegistry);
    }

    public CachedMfdsResponse call(
            final String operation,
            final Map<String, String> params,
            final String cacheKey
    ) {
        final Optional<CachedMfdsResponse> cached = cache.get(operation, cacheKey);
        if (cached.isPresent()) {
            cacheHitCounter.increment();
            log.debug("MFDS cache hit: operation={} key={}", operation, cacheKey);
            return cached.get();
        }

        cacheMissCounter.increment();
        log.info("MFDS API call: operation={} key={}", operation, cacheKey);
        final long startTime = System.currentTimeMillis();

        try {
            final StringBuilder urlBuilder = new StringBuilder()
                    .append("https://").append(properties.baseUrl()).append("/").append(operation)
                    .append("?serviceKey=").append(properties.serviceKey())
                    .append("&type=json");

            for (final Map.Entry<String, String> entry : params.entrySet()) {
                urlBuilder.append("&").append(entry.getKey()).append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }

            final URI uri = URI.create(urlBuilder.toString());
            final String responseBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            final long elapsed = System.currentTimeMillis() - startTime;
            log.info("MFDS API response: operation={} key={} elapsed={}ms", operation, cacheKey, elapsed);

            final CachedMfdsResponse response = parseResponse(operation, cacheKey, responseBody);
            cache.put(operation, cacheKey, response);
            return response;

        } catch (final BusinessException e) {
            throw e;
        } catch (final Exception e) {
            final long elapsed = System.currentTimeMillis() - startTime;
            log.error("MFDS API failed: operation={} key={} elapsed={}ms error={}",
                    operation, cacheKey, elapsed, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "MFDS API call failed: " + e.getMessage());
        }
    }

    private CachedMfdsResponse parseResponse(
            final String operation,
            final String cacheKey,
            final String responseBody
    ) {
        try {
            final JsonNode root = objectMapper.readTree(responseBody);

            final String resultCode = root.path("header").path("resultCode").asText();
            if (!RESULT_CODE_SUCCESS.equals(resultCode)) {
                final String resultMsg = root.path("header").path("resultMsg").asText();
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "MFDS API error: " + resultCode + " " + resultMsg);
            }

            final JsonNode body = root.path("body");
            final int totalCount = body.path("totalCount").asInt(0);

            final List<Map<String, Object>> items = new ArrayList<>();
            final JsonNode itemsNode = body.path("items");

            if (!itemsNode.isMissingNode() && !itemsNode.isNull()) {
                if (itemsNode.isArray()) {
                    for (final JsonNode item : itemsNode) {
                        items.add(objectMapper.convertValue(item,
                                new TypeReference<Map<String, Object>>() {
                                }));
                    }
                } else if (itemsNode.isObject()) {
                    final JsonNode itemNode = itemsNode.path("item");
                    if (itemNode.isArray()) {
                        for (final JsonNode item : itemNode) {
                            items.add(objectMapper.convertValue(item,
                                    new TypeReference<Map<String, Object>>() {
                                    }));
                        }
                    } else if (itemNode.isObject()) {
                        items.add(objectMapper.convertValue(itemNode,
                                new TypeReference<Map<String, Object>>() {
                                }));
                    }
                }
            }

            return new CachedMfdsResponse(operation, cacheKey, Instant.now(), totalCount, items);

        } catch (final BusinessException e) {
            throw e;
        } catch (final Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "MFDS response parse failed: " + e.getMessage());
        }
    }
}
