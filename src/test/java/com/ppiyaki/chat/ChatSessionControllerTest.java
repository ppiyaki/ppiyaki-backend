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
    void sendMessage_정상_요청시_200_응답을_반환한다() throws Exception {
        // given
        when(chatSessionService.sendMessage(any(), anyLong(), anyString()))
                .thenReturn("아스피린은 공복에 복용을 피하세요.");

        // when & then
        mockMvc.perform(post("/api/v1/chat/sessions/1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"아스피린 부작용이 뭐야?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("아스피린은 공복에 복용을 피하세요."));
    }

    @Test
    void sendMessage_존재하지_않는_세션이면_404를_반환한다() throws Exception {
        // given
        when(chatSessionService.sendMessage(any(), anyLong(), anyString()))
                .thenThrow(new SessionNotFoundException(999L));

        // when & then
        mockMvc.perform(post("/api/v1/chat/sessions/999/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void sendMessage_만료된_세션이면_410을_반환한다() throws Exception {
        // given
        when(chatSessionService.sendMessage(any(), anyLong(), anyString()))
                .thenThrow(new SessionExpiredException(1L));

        // when & then
        mockMvc.perform(post("/api/v1/chat/sessions/1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"hello\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void sendMessage_다른_사용자의_세션이면_403을_반환한다() throws Exception {
        // given
        when(chatSessionService.sendMessage(any(), anyLong(), anyString()))
                .thenThrow(new SessionAccessDeniedException(1L));

        // when & then
        mockMvc.perform(post("/api/v1/chat/sessions/1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"hello\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void sendMessage_빈_메시지면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/chat/sessions/1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
