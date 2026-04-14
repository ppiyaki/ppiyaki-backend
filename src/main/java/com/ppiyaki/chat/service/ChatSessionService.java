package com.ppiyaki.chat.service;

import com.ppiyaki.chat.domain.ChatMessage;
import com.ppiyaki.chat.domain.ChatSession;
import com.ppiyaki.chat.domain.MessageRole;
import com.ppiyaki.chat.repository.ChatMessageRepository;
import com.ppiyaki.chat.repository.ChatSessionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final long EXPIRATION_MINUTES = 5;
    private static final long SSE_TIMEOUT = 60_000L;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatClient chatClient;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Transactional
    public ChatSession createSession(final Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return chatSessionRepository.save(ChatSession.create(userId));
    }

    public SseEmitter sendMessageStream(final Long userId, final Long sessionId, final String message) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(message, "message must not be null");

        final List<Message> promptMessages = loadSessionAndBuildPrompt(userId, sessionId, message);

        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        final StringBuilder fullResponse = new StringBuilder();

        CompletableFuture.runAsync(() -> {
            try {
                chatClient.prompt(new Prompt(promptMessages))
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
                                saveMessages(sessionId, message, fullResponse.toString());
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                            } catch (final Exception e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(emitter::completeWithError)
                        .subscribe();
            } catch (final Exception e) {
                emitter.completeWithError(e);
            }
        }, executor);

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

        final List<Message> promptMessages = loadSessionAndBuildPrompt(userId, sessionId, transcribedText);

        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        final StringBuilder fullResponse = new StringBuilder();
        final SentenceBuffer sentenceBuffer = new SentenceBuffer();

        CompletableFuture.runAsync(() -> {
            try {
                chatClient.prompt(new Prompt(promptMessages))
                        .stream()
                        .content()
                        .doOnNext(token -> {
                            try {
                                fullResponse.append(token);
                                sentenceBuffer.append(token).ifPresent(sentence -> sendVoiceEvent(emitter, ttsService,
                                        sentence));
                            } catch (final Exception e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                sentenceBuffer.flush().ifPresent(sentence -> sendVoiceEvent(emitter, ttsService,
                                        sentence));
                                saveMessages(sessionId, transcribedText, fullResponse.toString());
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                            } catch (final Exception e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(emitter::completeWithError)
                        .subscribe();
            } catch (final Exception e) {
                emitter.completeWithError(e);
            }
        }, executor);

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

    @Transactional(readOnly = true)
    protected List<Message> loadSessionAndBuildPrompt(
            final Long userId, final Long sessionId, final String message) {
        final ChatSession chatSession = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (!chatSession.isOwnedBy(userId)) {
            throw new SessionAccessDeniedException(sessionId);
        }

        if (chatSession.isExpired(LocalDateTime.now(), EXPIRATION_MINUTES)) {
            throw new SessionExpiredException(sessionId);
        }

        final List<ChatMessage> recentMessages = chatMessageRepository.findTop20BySessionOrderByCreatedAtDescIdDesc(
                chatSession);

        return buildPromptMessages(recentMessages, message);
    }

    @Transactional
    public void saveMessages(final Long sessionId, final String userMessage, final String assistantResponse) {
        final ChatSession chatSession = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        chatMessageRepository.save(new ChatMessage(chatSession, MessageRole.USER, userMessage));
        chatMessageRepository.save(new ChatMessage(chatSession, MessageRole.ASSISTANT, assistantResponse));
        chatSessionRepository.save(chatSession);
    }

    private List<Message> buildPromptMessages(
            final List<ChatMessage> recentMessages,
            final String newMessage) {
        final List<Message> messages = new ArrayList<>();

        final List<ChatMessage> chronological = new ArrayList<>(recentMessages);
        Collections.reverse(chronological);

        for (final ChatMessage chatMessage : chronological) {
            if (chatMessage.getRole() == MessageRole.USER) {
                messages.add(new UserMessage(chatMessage.getContent()));
            } else {
                messages.add(new AssistantMessage(chatMessage.getContent()));
            }
        }

        messages.add(new UserMessage(newMessage));
        return messages;
    }
}
