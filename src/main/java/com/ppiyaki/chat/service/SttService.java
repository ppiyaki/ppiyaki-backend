package com.ppiyaki.chat.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class SttService {

    private static final String MODEL = "whisper-1";
    private static final String OPERATION = "stt_transcribe";

    private final OpenAiAudioTranscriptionModel transcriptionModel;
    private final MeterRegistry meterRegistry;

    public SttService(final OpenAiAudioTranscriptionModel transcriptionModel, final MeterRegistry meterRegistry) {
        this.transcriptionModel = transcriptionModel;
        this.meterRegistry = meterRegistry;
    }

    public String transcribe(final Resource audioResource, final String language) {
        final OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
                .language(language)
                .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                .build();

        final AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(audioResource, options);

        final Timer.Sample sample = Timer.start(meterRegistry);
        try {
            final var response = transcriptionModel.call(prompt);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                record("empty", sample);
                throw new IllegalStateException("STT 변환 결과가 비어있습니다.");
            }
            record("success", sample);
            return response.getResult().getOutput();
        } catch (final IllegalStateException e) {
            throw e;
        } catch (final RuntimeException e) {
            record("failed", sample);
            throw e;
        }
    }

    private void record(final String result, final Timer.Sample sample) {
        meterRegistry.counter("ppiyaki.llm.request.total",
                "model", MODEL, "operation", OPERATION, "result", result).increment();
        sample.stop(meterRegistry.timer("ppiyaki.llm.request.seconds",
                "model", MODEL, "operation", OPERATION, "result", result));
    }
}
