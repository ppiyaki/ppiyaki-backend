package com.ppiyaki.chat.service;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SttService {

    private final OpenAiAudioTranscriptionModel transcriptionModel;

    public String transcribe(final Resource audioResource, final String language) {
        Objects.requireNonNull(audioResource, "audioResource must not be null");
        Objects.requireNonNull(language, "language must not be null");

        final OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
                .language(language)
                .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                .build();

        final AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(audioResource, options);

        final var response = transcriptionModel.call(prompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("STT 변환 결과가 비어있습니다.");
        }
        return response.getResult().getOutput();
    }
}
