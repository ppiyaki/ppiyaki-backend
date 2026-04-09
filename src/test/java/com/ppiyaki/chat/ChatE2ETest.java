package com.ppiyaki.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.service.ChatService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatE2ETest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void chat_엔드포인트_성공_케이스() {
        // given
        when(chatService.chat(anyString())).thenReturn("아스피린은 공복에 복용을 피하세요.");

        // when & then
        given()
                .contentType(ContentType.JSON)
                .body("{\"message\": \"아스피린 복용 시 주의사항이 뭔가요?\"}")
                .when()
                .post("/api/v1/chat")
                .then()
                .statusCode(200)
                .body("message", notNullValue());
    }
}
