package com.ppiyaki.infrastructure.mfds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 식약처 의약품 낱알식별정보 OpenAPI 클라이언트 (MdcinGrnIdntfcInfoService03).
 * spec docs/features/pill-identification.md §5-3.
 *
 * <p>일괄 동기화용. 검색 키는 약명/업체명/일련번호로 한정되며 외형(각인·색·모양)은 응답 필드에만 포함되므로,
 * 본 client는 paging으로 전체를 받아 자체 인덱스(pill_identifications)에 적재하는 흐름이다.
 */
@Component
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class MdcinGrnIdntfcInfoClient {

    private static final Logger log = LoggerFactory.getLogger(MdcinGrnIdntfcInfoClient.class);
    private static final String BASE_URL = "https://apis.data.go.kr/1471000/MdcinGrnIdntfcInfoService03";
    private static final String OPERATION = "getMdcinGrnIdntfcInfoList03";
    private static final String RESULT_CODE_SUCCESS = "00";
    public static final int DEFAULT_NUM_OF_ROWS = 100;

    private final MfdsApiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MdcinGrnIdntfcInfoClient(
            final MfdsApiProperties properties,
            final RestClient.Builder restClientBuilder,
            final ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeout()));
        // 동기화 batch는 단건 호출보다 응답이 큼(numOfRows=100) → 여유 있게
        factory.setReadTimeout(Duration.ofSeconds(15));

        this.restClient = restClientBuilder.requestFactory(factory).build();
    }

    public PillPage fetchPage(final int pageNo, final int numOfRows) {
        final String url = BASE_URL + "/" + OPERATION
                + "?serviceKey=" + properties.serviceKey()
                + "&type=json"
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows;

        log.info("MDCIN_GRN API call: pageNo={} numOfRows={}", pageNo, numOfRows);
        final long startTime = System.currentTimeMillis();
        try {
            final String responseBody = restClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(String.class);
            final long elapsed = System.currentTimeMillis() - startTime;
            log.info("MDCIN_GRN API response: pageNo={} elapsed={}ms", pageNo, elapsed);
            return parseResponse(responseBody);
        } catch (final BusinessException e) {
            throw e;
        } catch (final Exception e) {
            final long elapsed = System.currentTimeMillis() - startTime;
            log.error("MDCIN_GRN API failed: pageNo={} elapsed={}ms error={}", pageNo, elapsed, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "MDCIN_GRN API call failed: " + e.getMessage());
        }
    }

    private PillPage parseResponse(final String responseBody) {
        try {
            final JsonNode root = objectMapper.readTree(responseBody);
            final String resultCode = root.path("header").path("resultCode").asText();
            if (!RESULT_CODE_SUCCESS.equals(resultCode)) {
                final String resultMsg = root.path("header").path("resultMsg").asText();
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "MDCIN_GRN API error: " + resultCode + " " + resultMsg);
            }

            final JsonNode body = root.path("body");
            final int totalCount = body.path("totalCount").asInt(0);

            final List<PillItem> items = new ArrayList<>();
            final JsonNode itemsNode = body.path("items");

            if (!itemsNode.isMissingNode() && !itemsNode.isNull()) {
                if (itemsNode.isArray()) {
                    for (final JsonNode item : itemsNode) {
                        items.add(toItem(item));
                    }
                } else if (itemsNode.isObject()) {
                    final JsonNode itemNode = itemsNode.path("item");
                    if (itemNode.isArray()) {
                        for (final JsonNode item : itemNode) {
                            items.add(toItem(item));
                        }
                    } else if (itemNode.isObject()) {
                        items.add(toItem(itemNode));
                    }
                }
            }
            return new PillPage(totalCount, items);
        } catch (final BusinessException e) {
            throw e;
        } catch (final Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "MDCIN_GRN parse failed: " + e.getMessage());
        }
    }

    private PillItem toItem(final JsonNode n) {
        return new PillItem(
                text(n, "ITEM_SEQ"),
                text(n, "ITEM_NAME"),
                text(n, "ENTP_NAME"),
                text(n, "PRINT_FRONT"),
                text(n, "PRINT_BACK"),
                text(n, "DRUG_SHAPE"),
                text(n, "COLOR_CLASS1"),
                text(n, "COLOR_CLASS2"),
                text(n, "LINE_FRONT"),
                text(n, "LINE_BACK"),
                text(n, "LENG_LONG"),
                text(n, "LENG_SHORT"),
                text(n, "THICK"),
                text(n, "CHART"),
                text(n, "ITEM_IMAGE"),
                text(n, "CLASS_NO"),
                text(n, "CLASS_NAME"),
                text(n, "ETC_OTC_NAME"),
                text(n, "MARK_CODE_FRONT"),
                text(n, "MARK_CODE_BACK"),
                text(n, "EDI_CODE"),
                text(n, "BIZRNO"),
                text(n, "CHANGE_DATE")
        );
    }

    private static String text(final JsonNode n, final String key) {
        final JsonNode v = n.path(key);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        final String s = v.asText();
        return s.isBlank() ? null : s;
    }

    public record PillPage(int totalCount, List<PillItem> items) {
    }

    public record PillItem(
            String itemSeq,
            String itemName,
            String entpName,
            String printFront,
            String printBack,
            String drugShape,
            String colorClass1,
            String colorClass2,
            String lineFront,
            String lineBack,
            String lengLong,
            String lengShort,
            String thick,
            String chart,
            String itemImage,
            String classNo,
            String className,
            String etcOtcName,
            String markCodeFront,
            String markCodeBack,
            String ediCode,
            String bizrno,
            String changeDate
    ) {
    }
}
