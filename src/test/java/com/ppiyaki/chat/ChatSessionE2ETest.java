package com.ppiyaki.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.common.auth.JwtProvider;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Flux;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.ai.openai.api-key=test-dummy-key")
@Import(ChatSessionE2ETest.MockChatClientConfig.class)
class ChatSessionE2ETest {

    @TestConfiguration
    static class MockChatClientConfig {

        @Bean
        @Primary
        public ChatClient mockChatClient() {
            final ChatClient chatClient = mock(ChatClient.class);
            final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            final ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.stream()).thenReturn(streamResponseSpec);
            when(streamResponseSpec.content()).thenReturn(Flux.just("아스피린은 ", "공복에 복용을 피하세요."));

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
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .post("/api/v1/chat/sessions")
                .then()
                .statusCode(201)
                .body("sessionId", notNullValue());
    }

    @Test
    void 인증_없이_요청하면_401을_반환한다() {
        given()
                .when()
                .post("/api/v1/chat/sessions")
                .then()
                .statusCode(401);
    }
}
