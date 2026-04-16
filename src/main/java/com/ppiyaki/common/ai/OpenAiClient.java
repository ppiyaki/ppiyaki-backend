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
                    .header("Authorization", "Bearer " + properties.apiKey().strip())
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
                    당신은 한국 병원 처방전 OCR 텍스트에서 약물 정보를 추출하는 전문가입니다.

                    ## 입력 특성
                    - OCR로 추출된 텍스트라 오인식·띄어쓰기 깨짐·줄바꿈 누락��� 빈번합니다.
                    - 처방전은 보통 표(테이블) 형식이지만 OCR이 좌→우, 위→아래로 읽어서
                      약품명 중간에 다른 컬럼(투약량·횟수·일수) 값이 섞여 들어옵니다.
                      예: "타이레놀정500mg 1정 3회 7일 부루펜정200mg 1정 2회 5일"
                    - 표 헤더("약품명", "1회 투약량", "1일 투여횟수", "총 투약일수" 등)가
                      데이터 사이에 섞여 있을 수 있습니다.

                    ## 추출 규칙
                    - 한국어 약물명을 식별하고, 뒤따르는 용량·복약주기를 매칭하세요.
                    - OCR 오인식이 있으면 가장 그럴듯한 한국어 약물명으로 보정하세요.
                      (예: "타이레놀정" ← "타이레 놀정", "부루펜정" ← "부루 펜정")
                    - 띄어쓰기가 깨진 텍스트도 문맥으로 식별하세요.
                    - 숫자·단위가 분리되어 있으면 합치세요 (예: "500 밀리 그람" → "500밀리그람").
                    - "1정", "2정" 같은 1회 투약량과 "3회", "1일3회" 같은 복약 횟수를 구분하세요.
                    - 표 헤더·환자명·병원명·의사명 등 비약물 텍스트는 무시하세요.
                    - 약물이 없으면 빈 배열 반환.

                    ## 출력 형식
                    {"medicines": [{"name": "약물명", "dosage": "용량", "schedule": "복약주기"}]}
                    - name: 약물명 (정제된 형태, 예: "타이레놀정500밀리그람")
                    - dosage: 1회 투약량 (예: "1정", "2캡슐"). 모르면 null.
                    - schedule: 복약주기 (예: "1일 3회 식후 30분"). 모르면 null.
                    - JSON만 반환. 다른 텍스트 없이.
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
