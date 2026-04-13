package com.ppiyaki.chat;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.service.TtsService;
import com.ppiyaki.common.auth.JwtProvider;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.ai.openai.api-key=test-dummy-key")
class TtsE2ETest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private TtsService ttsService;

    @Autowired
    private JwtProvider jwtProvider;

    private String accessToken;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        accessToken = jwtProvider.createAccessToken(1L);
    }

    @Test
    void tts_엔드포인트_성공_케이스() {
        // given
        final byte[] audioBytes = new byte[]{1, 2, 3, 4, 5};
        when(ttsService.synthesize(anyString())).thenReturn(audioBytes);

        // when
        final Response response = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("{\"text\": \"아스피린은 공복에 복용을 피하세요.\"}")
                .when()
                .post("/api/v1/tts");

        // then
        response.then()
                .statusCode(200)
                .header("Content-Type", equalTo("audio/mpeg"));
        assertThat(response.asByteArray()).isEqualTo(audioBytes);
    }
}
