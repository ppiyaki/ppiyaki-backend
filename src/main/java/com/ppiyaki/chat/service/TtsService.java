package com.ppiyaki.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TtsService {

    private final OpenAiAudioSpeechModel speechModel;

    public byte[] synthesize(final String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        return speechModel.call(text);
    }
}
