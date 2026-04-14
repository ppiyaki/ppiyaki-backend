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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
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
    @DisplayName("세션 생성 시 201 응답과 세션ID를 반환한다")
    void createSession_returns201WithSessionId() throws Exception {
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
    @DisplayName("존재하지 않는 세션이면 404를 반환한다")
    void sendMessage_notFoundSession_returns404() throws Exception {
        // given
        when(chatSessionService.sendMessageStream(any(), anyLong(), anyString()))
                .thenThrow(new SessionNotFoundException(999L));

        // when & then
        mockMvc.perform(post("/api/v1/chat/sessions/999/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("만료된 세션이면 410을 반환한다")
    void sendMessage_expiredSession_returns410() throws Exception {
        // given
        when(chatSessionService.sendMessageStream(any(), anyLong(), anyString()))
                .thenThrow(new SessionExpiredException(1L));

        // when & then
        mockMvc.perform(post("/api/v1/chat/sessions/1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"hello\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("다른 사용자의 세션이면 403을 반환한다")
    void sendMessage_accessDenied_returns403() throws Exception {
        // given
        when(chatSessionService.sendMessageStream(any(), anyLong(), anyString()))
                .thenThrow(new SessionAccessDeniedException(1L));

        // when & then
        mockMvc.perform(post("/api/v1/chat/sessions/1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"hello\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }
}
