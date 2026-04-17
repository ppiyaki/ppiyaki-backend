package com.ppiyaki.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.service.TtsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("정상 텍스트를 입력하면 음성 바이트를 반환한다")
    void synthesize_validText_returnsAudioBytes() {
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
    @DisplayName("null 텍스트를 입력하면 예외가 발생한다")
    void synthesize_nullText_throwsException() {
        // given & when & then
        assertThatThrownBy(() -> ttsService.synthesize(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("빈 텍스트를 입력하면 예외가 발생한다")
    void synthesize_emptyText_throwsException() {
        // given & when & then
        assertThatThrownBy(() -> ttsService.synthesize(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("공백만 있는 텍스트를 입력하면 예외가 발생한다")
    void synthesize_blankText_throwsException() {
        // given & when & then
        assertThatThrownBy(() -> ttsService.synthesize("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
