package com.ppiyaki.common.mfds;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppiyaki.common.mfds.MdcinGrnIdntfcInfoClient.PillPage;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MdcinGrnIdntfcInfoClient.parseResponse")
class MdcinGrnIdntfcInfoClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("items가 배열 형태 → totalCount + 모든 item 파싱")
    void parse_arrayItems() throws Exception {
        final String body = """
                {
                  "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                  "body": {
                    "totalCount": 2,
                    "items": [
                      {"ITEM_SEQ": "199500096", "ITEM_NAME": "타이레놀정500밀리그람",
                       "ENTP_NAME": "한국존슨앤드존슨판매(유)",
                       "PRINT_FRONT": "T", "DRUG_SHAPE": "장방형", "COLOR_CLASS1": "하양"},
                      {"ITEM_SEQ": "200000123", "ITEM_NAME": "이부프로펜정200mg",
                       "PRINT_FRONT": "I", "DRUG_SHAPE": "원형", "COLOR_CLASS1": "하양"}
                    ],
                    "pageNo": 1,
                    "numOfRows": 100
                  }
                }
                """;
        final PillPage page = invokeParse(body);

        assertThat(page.totalCount()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).itemSeq()).isEqualTo("199500096");
        assertThat(page.items().get(0).itemName()).isEqualTo("타이레놀정500밀리그람");
        assertThat(page.items().get(0).printFront()).isEqualTo("T");
        assertThat(page.items().get(0).colorClass1()).isEqualTo("하양");
    }

    @Test
    @DisplayName("items.item이 단일 객체 → 1건 파싱")
    void parse_singleObjectItem() throws Exception {
        final String body = """
                {
                  "header": {"resultCode": "00"},
                  "body": {
                    "totalCount": 1,
                    "items": {"item": {"ITEM_SEQ": "X", "ITEM_NAME": "단일"}}
                  }
                }
                """;
        final PillPage page = invokeParse(body);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).itemSeq()).isEqualTo("X");
    }

    @Test
    @DisplayName("items 빈 응답 → 빈 리스트")
    void parse_empty() throws Exception {
        final String body = """
                {"header": {"resultCode": "00"}, "body": {"totalCount": 0, "items": ""}}
                """;
        final PillPage page = invokeParse(body);
        assertThat(page.items()).isEmpty();
    }

    @Test
    @DisplayName("blank 문자열은 null로 정규화 (PRINT_FRONT가 \"\"면 null)")
    void parse_blankToNull() throws Exception {
        final String body = """
                {"header": {"resultCode": "00"}, "body": {"totalCount": 1, "items": [
                  {"ITEM_SEQ": "Y", "ITEM_NAME": "약", "PRINT_FRONT": "", "DRUG_SHAPE": "원형"}
                ]}}
                """;
        final PillPage page = invokeParse(body);
        assertThat(page.items().get(0).printFront()).isNull();
        assertThat(page.items().get(0).drugShape()).isEqualTo("원형");
    }

    /**
     * parseResponse는 private이므로 reflection으로 직접 호출.
     * 실제 HTTP 호출 없이 응답 파싱 로직만 단위 검증.
     */
    private PillPage invokeParse(final String body) throws Exception {
        final MdcinGrnIdntfcInfoClient client = new MdcinGrnIdntfcInfoClient(
                new MfdsApiProperties("test-key", "apis.data.go.kr/1471000/MfdsDurInfoService", 1000, 5000),
                org.springframework.web.client.RestClient.builder(),
                objectMapper);
        final Method m = MdcinGrnIdntfcInfoClient.class.getDeclaredMethod("parseResponse", String.class);
        m.setAccessible(true);
        return (PillPage) m.invoke(client, body);
    }
}
