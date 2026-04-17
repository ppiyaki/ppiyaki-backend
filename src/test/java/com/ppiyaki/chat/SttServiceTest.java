package com.ppiyaki.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.service.SttService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class SttServiceTest {

    private OpenAiAudioTranscriptionModel transcriptionModel;
    private SttService sttService;

    @BeforeEach
    void setUp() {
        transcriptionModel = mock(OpenAiAudioTranscriptionModel.class);
        sttService = new SttService(transcriptionModel);
    }

    @Test
    @DisplayName("정상 음성파일을 입력하면 텍스트를 반환한다")
    void transcribe_validAudio_returnsText() {
        // given
        final Resource audioResource = new ByteArrayResource(new byte[]{1, 2, 3});
        final String expectedText = "아스피린 복용 시 주의사항이 뭔가요?";

        final AudioTranscription transcription = new AudioTranscription(expectedText);
        final AudioTranscriptionResponse response = new AudioTranscriptionResponse(transcription);

        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenReturn(response);

        // when
        final String result = sttService.transcribe(audioResource, "ko");

        // then
        assertThat(result).isEqualTo(expectedText);
    }

    @Test
    @DisplayName("null 음성파일을 입력하면 예외가 발생한다")
    void transcribe_nullAudio_throwsException() {
        // given & when & then
        assertThatThrownBy(() -> sttService.transcribe(null, "ko"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("null 언어를 입력하면 예외가 발생한다")
    void transcribe_nullLanguage_throwsException() {
        // given
        final Resource audioResource = new ByteArrayResource(new byte[]{1, 2, 3});

        // when & then
        assertThatThrownBy(() -> sttService.transcribe(audioResource, null))
                .isInstanceOf(NullPointerException.class);
    }
}
