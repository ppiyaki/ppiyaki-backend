package com.ppiyaki.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.service.SttService;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.ai.openai.api-key=test-dummy-key")
class SttE2ETest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private SttService sttService;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void stt_엔드포인트_성공_케이스() {
        // given
        final String expectedText = "아스피린 복용 시 주의사항이 뭔가요?";
        when(sttService.transcribe(any(Resource.class), eq("ko"))).thenReturn(expectedText);

        // when & then
        given()
                .multiPart("file", "test.wav", new byte[]{1, 2, 3}, "audio/wav")
                .when()
                .post("/api/v1/stt")
                .then()
                .statusCode(200)
                .body("text", equalTo(expectedText));
    }
}
