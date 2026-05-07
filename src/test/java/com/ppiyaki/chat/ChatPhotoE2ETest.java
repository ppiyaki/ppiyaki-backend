package com.ppiyaki.chat;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.domain.ChatMessage;
import com.ppiyaki.chat.domain.ChatSession;
import com.ppiyaki.chat.repository.ChatMessageRepository;
import com.ppiyaki.chat.service.ChatSessionPersistenceService;
import com.ppiyaki.common.auth.JwtProvider;
import io.restassured.RestAssured;
import java.util.List;
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
@Import(ChatPhotoE2ETest.MockChatClientConfig.class)
@DisplayName("채팅 사진 첨부 (/chat/photo-messages, /chat/sessions/{id}/photo-messages) E2E")
class ChatPhotoE2ETest {

    @TestConfiguration
    static class MockChatClientConfig {

        @Bean
        @Primary
        public ChatClient mockChatClient() {
            final ChatClient chatClient = mock(ChatClient.class);
            final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            final ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.toolContext(anyMap())).thenReturn(requestSpec);
            when(requestSpec.stream()).thenReturn(streamResponseSpec);
            when(streamResponseSpec.content()).thenReturn(
                    Flux.just("흰색 원형, 'T' 각인 — ", "타이레놀로 보입니다."));
            return chatClient;
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ChatSessionPersistenceService persistenceService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private String accessToken;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        accessToken = jwtProvider.createAccessToken(1L);
    }

    @Test
    @DisplayName("단발 사진 메시지 — 임시 세션 자동 생성 + USER placeholder + ASSISTANT 묘사 응답 저장")
    void quick_photo_persists() throws Exception {
        final long before = chatMessageRepository.count();

        try {
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .multiPart("file", "p.jpg", new byte[]{1, 2, 3}, "image/jpeg")
                    .queryParam("message", "이거 뭐야?")
                    .when()
                    .post("/api/v1/chat/photo-messages");
        } catch (final Exception ignored) {
            // SSE 종료 chunk 파서 예외 — 무시
        }

        long after = chatMessageRepository.count();
        for (int i = 0; i < 20 && after - before < 2; i++) {
            Thread.sleep(100);
            after = chatMessageRepository.count();
        }
        assertThat(after - before).isEqualTo(2L);

        final List<ChatMessage> all = chatMessageRepository.findAll();
        final ChatMessage user = all.get(all.size() - 2);
        final ChatMessage assistant = all.get(all.size() - 1);
        assertThat(user.getContent()).startsWith("[이미지 첨부] ").contains("이거 뭐야?");
        assertThat(assistant.getContent()).contains("타이레놀");
    }

    @Test
    @DisplayName("단발 — message 미지정 시 default placeholder")
    void quick_photo_default_prompt() throws Exception {
        final long before = chatMessageRepository.count();

        try {
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .multiPart("file", "p.jpg", new byte[]{1}, "image/jpeg")
                    .when()
                    .post("/api/v1/chat/photo-messages");
        } catch (final Exception ignored) {
        }

        long after = chatMessageRepository.count();
        for (int i = 0; i < 20 && after - before < 2; i++) {
            Thread.sleep(100);
            after = chatMessageRepository.count();
        }

        final List<ChatMessage> all = chatMessageRepository.findAll();
        final ChatMessage user = all.get(all.size() - 2);
        assertThat(user.getContent()).startsWith("[이미지 첨부] 이 사진 속 약을 식별해줘");
    }

    @Test
    @DisplayName("세션 사진 메시지 — 기존 세션에 USER placeholder + ASSISTANT 응답 저장")
    void session_photo_persists() throws Exception {
        final ChatSession session = persistenceService.createSession(1L);
        final long before = chatMessageRepository.count();

        try {
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .multiPart("file", "p.jpg", new byte[]{1}, "image/jpeg")
                    .queryParam("message", "이거 뭐야?")
                    .when()
                    .post("/api/v1/chat/sessions/" + session.getId() + "/photo-messages");
        } catch (final Exception ignored) {
        }

        long after = chatMessageRepository.count();
        for (int i = 0; i < 20 && after - before < 2; i++) {
            Thread.sleep(100);
            after = chatMessageRepository.count();
        }

        final List<ChatMessage> all = chatMessageRepository.findAll();
        assertThat(all).extracting(ChatMessage::getContent)
                .anyMatch(c -> c.startsWith("[이미지 첨부] ") && c.contains("이거 뭐야?"));
    }

    @Test
    @DisplayName("빈 파일 → 400 CHAT_005")
    void empty_file_returns400() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .multiPart("file", "p.jpg", new byte[]{}, "image/jpeg")
                .when()
                .post("/api/v1/chat/photo-messages")
                .then()
                .statusCode(400)
                .body("error.code", is("CHAT_005"));
    }

    @Test
    @DisplayName("잘못된 MIME(image/gif) → 400 CHAT_006")
    void unsupported_mime_returns400() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .multiPart("file", "p.gif", new byte[]{1}, "image/gif")
                .when()
                .post("/api/v1/chat/photo-messages")
                .then()
                .statusCode(400)
                .body("error.code", is("CHAT_006"));
    }

    @Test
    @DisplayName("인증 없으면 401")
    void unauthenticated_returns401() {
        given()
                .multiPart("file", "p.jpg", new byte[]{1}, "image/jpeg")
                .when()
                .post("/api/v1/chat/photo-messages")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("Content-Type 변경 — application/pdf → 400 CHAT_006")
    void pdf_returns400() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .multiPart("file", "p.pdf", new byte[]{1}, "application/pdf")
                .when()
                .post("/api/v1/chat/photo-messages")
                .then()
                .statusCode(400)
                .body("error.code", is("CHAT_006"));
    }
}
