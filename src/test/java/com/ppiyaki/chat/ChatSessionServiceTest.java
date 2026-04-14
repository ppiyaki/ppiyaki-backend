package com.ppiyaki.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.domain.ChatSession;
import com.ppiyaki.chat.repository.ChatMessageRepository;
import com.ppiyaki.chat.repository.ChatSessionRepository;
import com.ppiyaki.chat.service.ChatSessionService;
import com.ppiyaki.chat.service.SessionAccessDeniedException;
import com.ppiyaki.chat.service.SessionExpiredException;
import com.ppiyaki.chat.service.SessionNotFoundException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

class ChatSessionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private ChatSessionRepository chatSessionRepository;
    private ChatMessageRepository chatMessageRepository;
    private ChatClient chatClient;
    private ChatSessionService chatSessionService;

    @BeforeEach
    void setUp() {
        chatSessionRepository = mock(ChatSessionRepository.class);
        chatMessageRepository = mock(ChatMessageRepository.class);
        chatClient = mock(ChatClient.class);
        chatSessionService = new ChatSessionService(
                chatSessionRepository, chatMessageRepository, chatClient);
    }

    @Test
    void createSession_새_세션을_생성한다() {
        // given
        final ChatSession chatSession = ChatSession.create(USER_ID);
        ReflectionTestUtils.setField(chatSession, "id", 1L);
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(chatSession);

        // when
        final ChatSession result = chatSessionService.createSession(USER_ID);

        // then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void sendMessageStream_존재하지_않는_세션이면_예외가_발생한다() {
        // given
        when(chatSessionRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatSessionService.sendMessageStream(USER_ID, 999L, "hello"))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void sendMessageStream_만료된_세션이면_예외가_발생한다() {
        // given
        final ChatSession chatSession = ChatSession.create(USER_ID);
        ReflectionTestUtils.setField(chatSession, "id", 1L);
        ReflectionTestUtils.setField(chatSession, "updatedAt",
                LocalDateTime.now().minusMinutes(10));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(chatSession));

        // when & then
        assertThatThrownBy(() -> chatSessionService.sendMessageStream(USER_ID, 1L, "hello"))
                .isInstanceOf(SessionExpiredException.class);
    }

    @Test
    void sendMessageStream_다른_사용자의_세션이면_예외가_발생한다() {
        // given
        final ChatSession chatSession = ChatSession.create(USER_ID);
        ReflectionTestUtils.setField(chatSession, "id", 1L);
        ReflectionTestUtils.setField(chatSession, "updatedAt", LocalDateTime.now());
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(chatSession));

        // when & then
        assertThatThrownBy(() -> chatSessionService.sendMessageStream(OTHER_USER_ID, 1L, "hello"))
                .isInstanceOf(SessionAccessDeniedException.class);
    }

    @Test
    void sendMessageStream_정상_세션이면_SseEmitter를_반환한다() {
        // given
        final ChatSession chatSession = ChatSession.create(USER_ID);
        ReflectionTestUtils.setField(chatSession, "id", 1L);
        ReflectionTestUtils.setField(chatSession, "updatedAt", LocalDateTime.now());
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(chatSession));
        when(chatMessageRepository.findTop20BySessionOrderByCreatedAtDescIdDesc(chatSession))
                .thenReturn(Collections.emptyList());

        final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        final ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("아스피린은 ", "공복에 복용을 피하세요."));
        when(chatMessageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(chatSessionRepository.save(any())).thenReturn(chatSession);

        // when
        final SseEmitter result = chatSessionService.sendMessageStream(USER_ID, 1L, "아스피린 부작용이 뭐야?");

        // then
        assertThat(result).isNotNull();
    }

    @Test
    void sendMessageStream_null_세션ID면_예외가_발생한다() {
        // given & when & then
        assertThatThrownBy(() -> chatSessionService.sendMessageStream(USER_ID, null, "hello"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sendMessageStream_null_메시지면_예외가_발생한다() {
        // given & when & then
        assertThatThrownBy(() -> chatSessionService.sendMessageStream(USER_ID, 1L, null))
                .isInstanceOf(NullPointerException.class);
    }
}
