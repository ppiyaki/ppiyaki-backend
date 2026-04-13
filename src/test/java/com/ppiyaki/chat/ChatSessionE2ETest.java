package com.ppiyaki.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.common.auth.JwtProvider;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.ai.openai.api-key=test-dummy-key")
class ChatSessionE2ETest {

    @TestConfiguration
    static class MockChatClientConfig {

        @Bean
        @Primary
        public ChatClient mockChatClient() {
            final ChatClient chatClient = mock(ChatClient.class);
            final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            final ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("아스피린은 공복에 복용을 피하세요.");

            return chatClient;
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProvider jwtProvider;

    private String accessToken;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        accessToken = jwtProvider.createAccessToken(1L);
    }

    @Test
    void 세션_생성_성공_케이스() {
        // when & then
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .post("/api/v1/chat/sessions")
                .then()
                .statusCode(201)
                .body("sessionId", notNullValue());
    }

    @Test
    void 세션_메시지_전송_성공_케이스() {
        // given
        final Long sessionId = given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .post("/api/v1/chat/sessions")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("sessionId");

        // when & then
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("{\"message\": \"아스피린 부작용이 뭐야?\"}")
                .when()
                .post("/api/v1/chat/sessions/" + sessionId + "/messages")
                .then()
                .statusCode(200)
                .body("message", equalTo("아스피린은 공복에 복용을 피하세요."));
    }

    @Test
    void 인증_없이_요청하면_401을_반환한다() {
        // when & then
        given()
                .when()
                .post("/api/v1/chat/sessions")
                .then()
                .statusCode(401);
    }
}
