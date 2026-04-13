package com.ppiyaki.chat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ppiyaki.chat.controller.TtsController;
import com.ppiyaki.chat.service.TtsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TtsController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.ppiyaki\\.common\\.auth\\..*"))
@AutoConfigureMockMvc(addFilters = false)
class TtsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TtsService ttsService;

    @Test
    void synthesize_정상_요청시_200_음성_바이트를_반환한다() throws Exception {
        // given
        final byte[] audioBytes = new byte[]{1, 2, 3, 4, 5};
        when(ttsService.synthesize(anyString())).thenReturn(audioBytes);

        // when & then
        mockMvc.perform(post("/api/v1/tts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"아스피린은 공복에 복용을 피하세요.\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andExpect(content().bytes(audioBytes));
    }

    @Test
    void synthesize_빈_텍스트_요청시_400_응답을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/tts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
