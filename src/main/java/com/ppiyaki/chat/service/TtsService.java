package com.ppiyaki.chat.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.stereotype.Service;

@Service
public class TtsService {

    private static final String MODEL = "tts-1";
    private static final String OPERATION = "tts_synthesize";

    private final OpenAiAudioSpeechModel speechModel;
    private final MeterRegistry meterRegistry;

    public TtsService(final OpenAiAudioSpeechModel speechModel, final MeterRegistry meterRegistry) {
        this.speechModel = speechModel;
        this.meterRegistry = meterRegistry;
    }

    public byte[] synthesize(final String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        final Timer.Sample sample = Timer.start(meterRegistry);
        try {
            final byte[] audio = speechModel.call(text);
            record("success", sample);
            return audio;
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
