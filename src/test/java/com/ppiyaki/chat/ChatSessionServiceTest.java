package com.ppiyaki.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.domain.ChatMessage;
import com.ppiyaki.chat.domain.ChatSession;
import com.ppiyaki.chat.domain.MessageRole;
import com.ppiyaki.chat.repository.ChatMessageRepository;
import com.ppiyaki.chat.repository.ChatSessionRepository;
import com.ppiyaki.chat.service.ChatSessionService;
import com.ppiyaki.chat.service.SessionExpiredException;
import com.ppiyaki.chat.service.SessionNotFoundException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

class ChatSessionServiceTest {

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
        final ChatSession chatSession = ChatSession.create();
        ReflectionTestUtils.setField(chatSession, "id", 1L);
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(chatSession);

        // when
        final ChatSession result = chatSessionService.createSession();

        // then
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void sendMessage_존재하지_않는_세션이면_예외가_발생한다() {
        // given
        when(chatSessionRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatSessionService.sendMessage(999L, "hello"))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void sendMessage_만료된_세션이면_예외가_발생한다() {
        // given
        final ChatSession chatSession = ChatSession.create();
        ReflectionTestUtils.setField(chatSession, "id", 1L);
        ReflectionTestUtils.setField(chatSession, "updatedAt",
                LocalDateTime.now().minusMinutes(10));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(chatSession));

        // when & then
        assertThatThrownBy(() -> chatSessionService.sendMessage(1L, "hello"))
                .isInstanceOf(SessionExpiredException.class);
    }

    @Test
    void sendMessage_정상_세션이면_LLM_응답을_반환하고_메시지를_저장한다() {
        // given
        final ChatSession chatSession = ChatSession.create();
        ReflectionTestUtils.setField(chatSession, "id", 1L);
        ReflectionTestUtils.setField(chatSession, "updatedAt", LocalDateTime.now());
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(chatSession));
        when(chatMessageRepository.findTop20BySessionOrderByCreatedAtDescIdDesc(chatSession))
                .thenReturn(Collections.emptyList());

        final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        final ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("아스피린은 공복에 복용을 피하세요.");
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(chatSession);

        // when
        final String response = chatSessionService.sendMessage(1L, "아스피린 부작용이 뭐야?");

        // then
        assertThat(response).isEqualTo("아스피린은 공복에 복용을 피하세요.");

        verify(chatMessageRepository).findTop20BySessionOrderByCreatedAtDescIdDesc(chatSession);

        final ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        final List<ChatMessage> savedMessages = captor.getAllValues();

        assertThat(savedMessages.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(savedMessages.get(0).getContent()).isEqualTo("아스피린 부작용이 뭐야?");
        assertThat(savedMessages.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(savedMessages.get(1).getContent()).isEqualTo("아스피린은 공복에 복용을 피하세요.");

        verify(chatSessionRepository).save(any(ChatSession.class));
    }

    @Test
    void sendMessage_null_세션ID면_예외가_발생한다() {
        // given & when & then
        assertThatThrownBy(() -> chatSessionService.sendMessage(null, "hello"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sendMessage_null_메시지면_예외가_발생한다() {
        // given & when & then
        assertThatThrownBy(() -> chatSessionService.sendMessage(1L, null))
                .isInstanceOf(NullPointerException.class);
    }
}
