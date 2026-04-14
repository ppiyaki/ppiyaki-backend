package com.ppiyaki.chat.service;

import com.ppiyaki.chat.domain.ChatMessage;
import com.ppiyaki.chat.domain.ChatSession;
import com.ppiyaki.chat.domain.MessageRole;
import com.ppiyaki.chat.repository.ChatMessageRepository;
import com.ppiyaki.chat.repository.ChatSessionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatSessionPersistenceService {

    private static final long EXPIRATION_MINUTES = 5;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatSession createSession(final Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return chatSessionRepository.save(ChatSession.create(userId));
    }

    @Transactional(readOnly = true)
    public List<Message> loadSessionAndBuildPrompt(
            final Long userId, final Long sessionId, final String message) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(message, "message must not be null");

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
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");
        Objects.requireNonNull(assistantResponse, "assistantResponse must not be null");

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
