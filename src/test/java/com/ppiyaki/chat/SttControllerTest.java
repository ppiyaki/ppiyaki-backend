package com.ppiyaki.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ppiyaki.chat.controller.SttController;
import com.ppiyaki.chat.service.SttService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SttController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.ppiyaki\\.common\\.auth\\..*"))
@AutoConfigureMockMvc(addFilters = false)
class SttControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SttService sttService;

    @Test
    void transcribe_정상_요청시_200_응답을_반환한다() throws Exception {
        // given
        final String expectedText = "아스피린 복용 시 주의사항이 뭔가요?";
        when(sttService.transcribe(any(Resource.class), eq("ko"))).thenReturn(expectedText);

        final MockMultipartFile audioFile = new MockMultipartFile(
                "file", "test.wav", "audio/wav", new byte[]{1, 2, 3});

        // when & then
        mockMvc.perform(multipart("/api/v1/stt").file(audioFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(expectedText));
    }

    @Test
    void transcribe_파일_없이_요청시_400_응답을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(multipart("/api/v1/stt"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transcribe_language_파라미터를_지정하면_해당_언어로_변환한다() throws Exception {
        // given
        final String expectedText = "What are the side effects?";
        when(sttService.transcribe(any(Resource.class), eq("en"))).thenReturn(expectedText);

        final MockMultipartFile audioFile = new MockMultipartFile(
                "file", "test.wav", "audio/wav", new byte[]{1, 2, 3});

        // when & then
        mockMvc.perform(multipart("/api/v1/stt").file(audioFile).param("language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(expectedText));
    }
}
