package com.ppiyaki.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.service.TtsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;

class TtsServiceTest {

    private OpenAiAudioSpeechModel speechModel;
    private TtsService ttsService;

    @BeforeEach
    void setUp() {
        speechModel = mock(OpenAiAudioSpeechModel.class);
        ttsService = new TtsService(speechModel);
    }

    @Test
    void synthesize_정상_텍스트를_입력하면_음성_바이트를_반환한다() {
        // given
        final String text = "아스피린은 공복에 복용을 피하세요.";
        final byte[] expectedAudio = new byte[]{1, 2, 3, 4, 5};
        when(speechModel.call(text)).thenReturn(expectedAudio);

        // when
        final byte[] result = ttsService.synthesize(text);

        // then
        assertThat(result).isEqualTo(expectedAudio);
    }

    @Test
    void synthesize_null_텍스트를_입력하면_예외가_발생한다() {
        // given & when & then
        assertThatThrownBy(() -> ttsService.synthesize(null))
                .isInstanceOf(NullPointerException.class);
    }
}
