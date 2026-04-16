package com.ppiyaki.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "openai", name = "api-key")
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final OpenAiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiClient(
            final OpenAiProperties properties,
            final RestClient.Builder restClientBuilder,
            final ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = restClientBuilder.requestFactory(factory).build();
    }

    public List<ExtractedMedicine> extractMedicines(final String maskedOcrText) {
        log.info("OpenAI extractMedicines: textLength={}", maskedOcrText.length());
        final long startTime = System.currentTimeMillis();

        try {
            final String model = properties.model() != null ? properties.model() : "gpt-5.4-nano";
            final String requestBody = buildRequest(model, maskedOcrText);

            final String responseBody = restClient.post()
                    .uri(API_URL)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            final long elapsed = System.currentTimeMillis() - startTime;
            log.info("OpenAI response: elapsed={}ms", elapsed);

            return parseResponse(responseBody);

        } catch (final BusinessException e) {
            throw e;
        } catch (final Exception e) {
            final long elapsed = System.currentTimeMillis() - startTime;
            log.error("OpenAI failed: elapsed={}ms error={}", elapsed, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "AI extraction failed: " + e.getMessage());
        }
    }

    private String buildRequest(final String model, final String ocrText) {
        try {
            final String systemPrompt = """
                    당신은 처방전 OCR 텍스트에서 약물 정보를 추출하는 전문가입니다.
                    입력된 텍스트에서 약물명, 용량, 복약주기를 추출하여 JSON 배열로 반환하세요.

                    규칙:
                    - 약물 정보만 추출. 환자명, 병원명, 의사명 등 개인정보는 절대 포함하지 마세요.
                    - 각 약물은 {"name": "약물명", "dosage": "용량", "schedule": "복약주기"} 형태로.
                    - 약물이 없으면 빈 배열 [] 반환.
                    - JSON 배열만 반환. 다른 텍스트 없이.
                    """;

            final var request = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", "다음 처방전 OCR 텍스트에서 약물 정보를 추출하세요:\n\n" + ocrText)
                    ),
                    "temperature", 0.0,
                    "response_format", Map.of("type", "json_object")
            );

            return objectMapper.writeValueAsString(request);
        } catch (final Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "AI request build failed: " + e.getMessage());
        }
    }

    private List<ExtractedMedicine> parseResponse(final String responseBody) {
        try {
            final JsonNode root = objectMapper.readTree(responseBody);
            final String content = root.path("choices").get(0)
                    .path("message").path("content").asText();

            final JsonNode parsed = objectMapper.readTree(content);

            final JsonNode medicines;
            if (parsed.isArray()) {
                medicines = parsed;
            } else if (parsed.has("medicines")) {
                medicines = parsed.path("medicines");
            } else {
                medicines = objectMapper.createArrayNode();
            }

            final List<ExtractedMedicine> result = new ArrayList<>();
            for (final JsonNode item : medicines) {
                result.add(new ExtractedMedicine(
                        item.path("name").asText(null),
                        item.path("dosage").asText(null),
                        item.path("schedule").asText(null)
                ));
            }

            log.info("OpenAI extracted {} medicines", result.size());
            return result;

        } catch (final Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "AI response parse failed: " + e.getMessage());
        }
    }

    public record ExtractedMedicine(
            String name,
            String dosage,
            String schedule
    ) {
    }
}
