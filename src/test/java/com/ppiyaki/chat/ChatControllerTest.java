package com.ppiyaki.chat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ppiyaki.chat.controller.ChatController;
import com.ppiyaki.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private ChatClient chatClient;

    @Test
    void chat_정상_요청시_200_응답을_반환한다() throws Exception {
        // given
        final String responseMessage = "아스피린은 공복에 복용을 피하세요.";
        when(chatService.chat(anyString())).thenReturn(responseMessage);

        // when & then
        mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"아스피린 복용 시 주의사항이 뭔가요?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(responseMessage));
    }

    @Test
    void chat_빈_메시지_요청시_400_응답을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
