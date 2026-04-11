package com.ppiyaki.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.domain.ChatSession;
import com.ppiyaki.chat.service.ChatSessionService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.ai.openai.api-key=test-dummy-key")
class ChatSessionE2ETest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private ChatSessionService chatSessionService;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void 세션_생성_성공_케이스() {
        // given
        final ChatSession chatSession = ChatSession.create();
        ReflectionTestUtils.setField(chatSession, "id", 1L);
        when(chatSessionService.createSession()).thenReturn(chatSession);

        // when & then
        given()
                .when()
                .post("/api/v1/chat/sessions")
                .then()
                .statusCode(201)
                .body("sessionId", notNullValue());
    }

    @Test
    void 세션_메시지_전송_성공_케이스() {
        // given
        when(chatSessionService.sendMessage(anyLong(), anyString()))
                .thenReturn("아스피린은 공복에 복용을 피하세요.");

        // when & then
        given()
                .contentType(ContentType.JSON)
                .body("{\"message\": \"아스피린 부작용이 뭐야?\"}")
                .when()
                .post("/api/v1/chat/sessions/1/messages")
                .then()
                .statusCode(200)
                .body("message", equalTo("아스피린은 공복에 복용을 피하세요."));
    }
}
