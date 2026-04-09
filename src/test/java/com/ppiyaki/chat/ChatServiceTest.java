package com.ppiyaki.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class ChatServiceTest {

    private ChatClient chatClient;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        chatService = new ChatService(chatClient);
    }

    @Test
    void chat_정상_메시지를_입력하면_응답을_반환한다() {
        // given
        final String message = "아스피린 복용 시 주의사항이 뭔가요?";
        final String expectedResponse = "아스피린은 공복에 복용을 피하세요.";

        final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        final ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(message)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(expectedResponse);

        // when
        final String response = chatService.chat(message);

        // then
        assertThat(response).isEqualTo(expectedResponse);
    }

    @Test
    void chat_null_메시지를_입력하면_예외가_발생한다() {
        // given & when & then
        assertThatThrownBy(() -> chatService.chat(null))
                .isInstanceOf(NullPointerException.class);
    }
}
