package com.ppiyaki.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ppiyaki.chat.controller.ChatSessionController;
import com.ppiyaki.chat.domain.ChatSession;
import com.ppiyaki.chat.service.ChatSessionService;
import com.ppiyaki.chat.service.SessionAccessDeniedException;
import com.ppiyaki.chat.service.SessionExpiredException;
import com.ppiyaki.chat.service.SessionNotFoundException;
import com.ppiyaki.chat.service.SttService;
import com.ppiyaki.chat.service.TtsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ChatSessionController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.ppiyaki\\.common\\.auth\\..*"))
@AutoConfigureMockMvc(addFilters = false)
class ChatSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatSessionService chatSessionService;

    @MockitoBean
    private SttService sttService;

    @MockitoBean
    private TtsService ttsService;

    @Test
    void createSession_201_응답과_세션ID를_반환한다() throws Exception {
        // given
        final ChatSession chatSession = ChatSession.create(1L);
        ReflectionTestUtils.setField(chatSession, "id", 1L);
        when(chatSessionService.createSession(any())).thenReturn(chatSession);

        // when & then
        mockMvc.perform(post("/api/v1/chat/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(1));
    }

    @Test
    void sendMessage_존재하지_않는_세션이면_예외가_발생한다() {
        // given
        when(chatSessionService.sendMessageStream(any(), anyLong(), anyString()))
                .thenThrow(new SessionNotFoundException(999L));

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(SessionNotFoundException.class, () -> {
            chatSessionService.sendMessageStream(null, 999L, "hello");
        });
    }

    @Test
    void sendMessage_만료된_세션이면_예외가_발생한다() {
        // given
        when(chatSessionService.sendMessageStream(any(), anyLong(), anyString()))
                .thenThrow(new SessionExpiredException(1L));

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(SessionExpiredException.class, () -> {
            chatSessionService.sendMessageStream(null, 1L, "hello");
        });
    }

    @Test
    void sendMessage_다른_사용자의_세션이면_예외가_발생한다() {
        // given
        when(chatSessionService.sendMessageStream(any(), anyLong(), anyString()))
                .thenThrow(new SessionAccessDeniedException(1L));

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(SessionAccessDeniedException.class, () -> {
            chatSessionService.sendMessageStream(null, 1L, "hello");
        });
    }
}
