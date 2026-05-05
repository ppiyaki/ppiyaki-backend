package com.ppiyaki.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.repository.ChatMessageRepository;
import com.ppiyaki.common.auth.JwtProvider;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
@Import(ChatQuickE2ETest.MockChatClientConfig.class)
@DisplayName("단발 채팅 (/api/v1/chat/messages, /api/v1/chat/voice-messages) E2E")
class ChatQuickE2ETest {

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
            when(streamResponseSpec.content()).thenReturn(Flux.just("타이레놀은 ", "식후에 드세요."));

            return chatClient;
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private String accessToken;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        accessToken = jwtProvider.createAccessToken(1L);
    }

    @Test
    @DisplayName("단발 텍스트 — 임시 세션 자동 생성 후 DB에 USER+ASSISTANT 메시지 저장")
    void quick_text_message_persists_after_stream_completes() throws Exception {
        final long before = chatMessageRepository.count();

        // SSE chunked 응답이라 RestAssured 본문 파싱은 SSE 종료 시 chunk 부족 예외 던짐.
        // status code는 RestAssured가 받지만 body 추출 시 ConnectionClosed가 발생해
        // try/catch로 무시하고 DB로 결과 검증.
        try {
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body("{\"message\": \"타이레놀 어떻게 먹어?\"}")
                    .when()
                    .post("/api/v1/chat/messages");
        } catch (final Exception ignored) {
            // SSE 스트림 종료 시 chunk 파서 예외 — 무시
        }

        // saveMessages는 doOnComplete에서 동기 호출되므로 status 응답 시점엔 완료됨.
        // 그러나 백그라운드 Executor라 약간의 여유가 필요할 수 있어 wait-and-retry.
        long after = chatMessageRepository.count();
        for (int i = 0; i < 20 && after - before < 2; i++) {
            Thread.sleep(100);
            after = chatMessageRepository.count();
        }
        org.assertj.core.api.Assertions.assertThat(after - before).isEqualTo(2L);
    }

    @Test
    @DisplayName("단발 텍스트 — 인증 없이 호출하면 401")
    void quick_text_unauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"message\": \"hi\"}")
                .when()
                .post("/api/v1/chat/messages")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("단발 음성 — 빈 파일은 400 CHAT_004")
    void quick_voice_empty_file() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("multipart/form-data")
                .multiPart("file", "empty.m4a", new byte[0], "audio/mp4")
                .multiPart("language", "ko")
                .when()
                .post("/api/v1/chat/voice-messages")
                .then()
                .statusCode(400)
                .body("error.code", is("CHAT_004"));
    }

    @Test
    @DisplayName("단발 메시지 — 빈 message는 400 (jakarta.validation @NotBlank)")
    void quick_text_blank_message() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("{\"message\": \"\"}")
                .when()
                .post("/api/v1/chat/messages")
                .then()
                .statusCode(400)
                .body("error.code", containsString("COMMON"));
    }
}
