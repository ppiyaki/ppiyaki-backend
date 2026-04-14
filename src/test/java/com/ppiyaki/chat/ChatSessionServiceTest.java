package com.ppiyaki.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.service.ChatSessionPersistenceService;
import com.ppiyaki.chat.service.ChatSessionService;
import com.ppiyaki.chat.service.SessionAccessDeniedException;
import com.ppiyaki.chat.service.SessionExpiredException;
import com.ppiyaki.chat.service.SessionNotFoundException;
import java.util.Collections;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

class ChatSessionServiceTest {

    private static final Long USER_ID = 1L;

    private ChatSessionPersistenceService persistenceService;
    private ChatClient chatClient;
    private ChatSessionService chatSessionService;

    @BeforeEach
    void setUp() {
        persistenceService = mock(ChatSessionPersistenceService.class);
        chatClient = mock(ChatClient.class);
        final Executor executor = Runnable::run;
        chatSessionService = new ChatSessionService(persistenceService, chatClient, executor);
    }

    @Test
    @DisplayName("존재하지 않는 세션이면 예외가 발생한다")
    void sendMessageStream_notFound_throwsException() {
        // given
        when(persistenceService.loadSessionAndBuildPrompt(anyLong(), anyLong(), anyString()))
                .thenThrow(new SessionNotFoundException(999L));

        // when & then
        assertThatThrownBy(() -> chatSessionService.sendMessageStream(USER_ID, 999L, "hello"))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("만료된 세션이면 예외가 발생한다")
    void sendMessageStream_expired_throwsException() {
        // given
        when(persistenceService.loadSessionAndBuildPrompt(anyLong(), anyLong(), anyString()))
                .thenThrow(new SessionExpiredException(1L));

        // when & then
        assertThatThrownBy(() -> chatSessionService.sendMessageStream(USER_ID, 1L, "hello"))
                .isInstanceOf(SessionExpiredException.class);
    }

    @Test
    @DisplayName("다른 사용자의 세션이면 예외가 발생한다")
    void sendMessageStream_accessDenied_throwsException() {
        // given
        when(persistenceService.loadSessionAndBuildPrompt(anyLong(), anyLong(), anyString()))
                .thenThrow(new SessionAccessDeniedException(1L));

        // when & then
        assertThatThrownBy(() -> chatSessionService.sendMessageStream(2L, 1L, "hello"))
                .isInstanceOf(SessionAccessDeniedException.class);
    }

    @Test
    @DisplayName("정상 세션이면 SseEmitter를 반환한다")
    void sendMessageStream_success_returnsSseEmitter() {
        // given
        when(persistenceService.loadSessionAndBuildPrompt(anyLong(), anyLong(), anyString()))
                .thenReturn(Collections.emptyList());

        final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        final ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("아스피린은 ", "공복에 복용을 피하세요."));

        // when
        final SseEmitter result = chatSessionService.sendMessageStream(USER_ID, 1L, "아스피린 부작용이 뭐야?");

        // then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("null 세션ID면 예외가 발생한다")
    void sendMessageStream_nullSessionId_throwsException() {
        // given & when & then
        assertThatThrownBy(() -> chatSessionService.sendMessageStream(USER_ID, null, "hello"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("null 메시지면 예외가 발생한다")
    void sendMessageStream_nullMessage_throwsException() {
        // given & when & then
        assertThatThrownBy(() -> chatSessionService.sendMessageStream(USER_ID, 1L, null))
                .isInstanceOf(NullPointerException.class);
    }
}
