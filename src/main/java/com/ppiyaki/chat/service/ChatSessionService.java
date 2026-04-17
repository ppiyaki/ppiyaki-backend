package com.ppiyaki.chat.service;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final long SSE_TIMEOUT = 60_000L;

    private final ChatSessionPersistenceService persistenceService;
    private final ChatClient chatClient;
    private final Executor chatStreamExecutor;

    public SseEmitter sendMessageStream(final Long userId, final Long sessionId, final String message) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(message, "message must not be null");

        final List<Message> promptMessages = persistenceService.loadSessionAndBuildPrompt(userId, sessionId, message);

        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        final StringBuilder fullResponse = new StringBuilder();

        chatStreamExecutor.execute(() -> {
            final Disposable subscription = chatClient.prompt(new Prompt(promptMessages))
                    .stream()
                    .content()
                    .doOnNext(token -> {
                        try {
                            fullResponse.append(token);
                            emitter.send(SseEmitter.event().data(token));
                        } catch (final Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            persistenceService.saveMessages(sessionId, message, fullResponse.toString());
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                        } catch (final Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(emitter::completeWithError)
                    .subscribe();

            emitter.onTimeout(subscription::dispose);
            emitter.onCompletion(subscription::dispose);
            emitter.onError(error -> subscription.dispose());
        });

        return emitter;
    }

    public SseEmitter sendVoiceMessageStream(
            final Long userId,
            final Long sessionId,
            final String transcribedText,
            final TtsService ttsService) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(transcribedText, "transcribedText must not be null");
        Objects.requireNonNull(ttsService, "ttsService must not be null");

        final List<Message> promptMessages = persistenceService.loadSessionAndBuildPrompt(userId, sessionId,
                transcribedText);

        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        final StringBuilder fullResponse = new StringBuilder();
        final SentenceBuffer sentenceBuffer = new SentenceBuffer();

        chatStreamExecutor.execute(() -> {
            final Disposable subscription = chatClient.prompt(new Prompt(promptMessages))
                    .stream()
                    .content()
                    .doOnNext(token -> {
                        try {
                            fullResponse.append(token);
                            sentenceBuffer.append(token)
                                    .ifPresent(sentence -> sendVoiceEvent(emitter, ttsService, sentence));
                        } catch (final Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            sentenceBuffer.flush()
                                    .ifPresent(sentence -> sendVoiceEvent(emitter, ttsService, sentence));
                            persistenceService.saveMessages(sessionId, transcribedText, fullResponse.toString());
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                        } catch (final Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(emitter::completeWithError)
                    .subscribe();

            emitter.onTimeout(subscription::dispose);
            emitter.onCompletion(subscription::dispose);
            emitter.onError(error -> subscription.dispose());
        });

        return emitter;
    }

    private void sendVoiceEvent(final SseEmitter emitter, final TtsService ttsService, final String sentence) {
        try {
            final byte[] audioBytes = ttsService.synthesize(sentence);
            final String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);
            final String json = "{\"text\":\"" + escapeJson(sentence)
                    + "\",\"audio\":\"" + audioBase64 + "\"}";
            emitter.send(SseEmitter.event().data(json));
        } catch (final Exception e) {
            emitter.completeWithError(e);
        }
    }

    private String escapeJson(final String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
