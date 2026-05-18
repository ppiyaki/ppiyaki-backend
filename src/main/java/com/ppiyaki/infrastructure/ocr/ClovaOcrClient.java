package com.ppiyaki.infrastructure.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.infrastructure.observability.ExternalApiMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "clova.ocr", name = "secret")
public class ClovaOcrClient {

    private static final Logger log = LoggerFactory.getLogger(ClovaOcrClient.class);
    private static final String API = "clova";
    private static final String OPERATION = "ocr";

    private final ClovaOcrProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public ClovaOcrClient(
            final ClovaOcrProperties properties,
            final RestClient.Builder restClientBuilder,
            final ObjectMapper objectMapper,
            final MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;

        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = restClientBuilder.requestFactory(factory).build();
    }

    public OcrResult ocr(final byte[] imageBytes, final String imageFormat) {
        log.info("Clova OCR call: format={} size={}bytes", imageFormat, imageBytes.length);
        final Timer.Sample sample = Timer.start(meterRegistry);

        try {
            final String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            final String requestBody = buildRequestBody(base64Image, imageFormat);

            final byte[] responseBytes = restClient.post()
                    .uri(properties.invokeUrl())
                    .header("X-OCR-SECRET", properties.secret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);
            final String responseBody = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);

            final OcrResult result = parseResponse(responseBody);
            ExternalApiMetrics.record(meterRegistry, API, OPERATION, ExternalApiMetrics.RESULT_SUCCESS, sample);
            return result;

        } catch (final BusinessException e) {
            ExternalApiMetrics.record(meterRegistry, API, OPERATION, ExternalApiMetrics.RESULT_FAILED, sample);
            throw e;
        } catch (final Exception e) {
            ExternalApiMetrics.record(meterRegistry, API, OPERATION, ExternalApiMetrics.RESULT_FAILED, sample);
            log.error("Clova OCR failed: error={}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "OCR failed: " + e.getMessage());
        }
    }

    private String buildRequestBody(final String base64Image, final String format) {
        try {
            final var request = objectMapper.createObjectNode();
            request.put("version", "V2");
            request.put("requestId", UUID.randomUUID().toString());
            request.put("timestamp", System.currentTimeMillis());

            final var image = objectMapper.createObjectNode();
            image.put("format", format);
            image.put("data", base64Image);
            image.put("name", "prescription");

            final var images = objectMapper.createArrayNode();
            images.add(image);
            request.set("images", images);

            return objectMapper.writeValueAsString(request);
        } catch (final Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "OCR request build failed: " + e.getMessage());
        }
    }

    private OcrResult parseResponse(final String responseBody) {
        try {
            final JsonNode root = objectMapper.readTree(responseBody);
            final JsonNode images = root.path("images");

            if (images.isEmpty() || !images.isArray()) {
                return new OcrResult("", List.of());
            }

            final JsonNode firstImage = images.get(0);
            final JsonNode fields = firstImage.path("fields");

            final StringBuilder fullText = new StringBuilder();
            final List<OcrToken> tokens = new ArrayList<>();

            if (fields.isArray()) {
                for (final JsonNode field : fields) {
                    final String text = field.path("inferText").asText("");
                    fullText.append(text).append(" ");

                    final JsonNode bounding = field.path("boundingPoly").path("vertices");
                    if (bounding.isArray() && bounding.size() >= 4) {
                        final int x = bounding.get(0).path("x").asInt();
                        final int y = bounding.get(0).path("y").asInt();
                        final int x2 = bounding.get(2).path("x").asInt();
                        final int y2 = bounding.get(2).path("y").asInt();
                        tokens.add(new OcrToken(text, x, y, x2 - x, y2 - y));
                    }
                }
            }

            return new OcrResult(fullText.toString().strip(), tokens);

        } catch (final Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "OCR response parse failed: " + e.getMessage());
        }
    }

    public record OcrResult(
            String fullText,
            List<OcrToken> tokens
    ) {
    }

    public record OcrToken(
            String text,
            int x,
            int y,
            int width,
            int height
    ) {
    }
}
