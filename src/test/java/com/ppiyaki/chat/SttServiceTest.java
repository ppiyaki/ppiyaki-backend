package com.ppiyaki.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.chat.service.SttService;
import org.junit.jupiter.api.BeforeEach;
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
    void transcribe_정상_음성파일을_입력하면_텍스트를_반환한다() {
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
    void transcribe_null_음성파일을_입력하면_예외가_발생한다() {
        // given & when & then
        assertThatThrownBy(() -> sttService.transcribe(null, "ko"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void transcribe_null_언어를_입력하면_예외가_발생한다() {
        // given
        final Resource audioResource = new ByteArrayResource(new byte[]{1, 2, 3});

        // when & then
        assertThatThrownBy(() -> sttService.transcribe(audioResource, null))
                .isInstanceOf(NullPointerException.class);
    }
}
