package com.ppiyaki.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.service.SttService;
import com.ppiyaki.chat.service.TtsService;
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
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.ai.openai.api-key=test-dummy-key")
@Import(VoiceMessageE2ETest.MockChatClientConfig.class)
class VoiceMessageE2ETest {

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
            when(streamResponseSpec.content())
                    .thenReturn(Flux.just("아스피린은 공복에 복용을 피하세요."));

            return chatClient;
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private SttService sttService;

    @MockitoBean
    private TtsService ttsService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        accessToken = jwtProvider.createAccessToken(1L);
    }

    @Test
    void voice_messages_엔드포인트_세션생성_후_호출_성공() {
        // given
        when(sttService.transcribe(any(Resource.class), anyString()))
                .thenReturn("아스피린 부작용이 뭐야?");
        when(ttsService.synthesize(anyString()))
                .thenReturn(new byte[]{1, 2, 3, 4, 5});

        final Long sessionId = given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .post("/api/v1/chat/sessions")
                .then()
                .statusCode(201)
                .body("sessionId", notNullValue())
                .extract()
                .jsonPath()
                .getLong("sessionId");

        // when & then - SSE 응답이므로 200 확인만
        given()
                .header("Authorization", "Bearer " + accessToken)
                .multiPart("file", "test.wav", new byte[]{1, 2, 3}, "audio/wav")
                .when()
                .post("/api/v1/chat/sessions/" + sessionId + "/voice-messages")
                .then()
                .statusCode(200);
    }
}
