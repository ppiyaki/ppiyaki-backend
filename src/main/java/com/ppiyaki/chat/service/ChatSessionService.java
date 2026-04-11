package com.ppiyaki.chat.service;

import com.ppiyaki.chat.domain.ChatMessage;
import com.ppiyaki.chat.domain.ChatSession;
import com.ppiyaki.chat.domain.MessageRole;
import com.ppiyaki.chat.repository.ChatMessageRepository;
import com.ppiyaki.chat.repository.ChatSessionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final long EXPIRATION_MINUTES = 5;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatClient chatClient;

    @Transactional
    public ChatSession createSession() {
        return chatSessionRepository.save(ChatSession.create());
    }

    @Transactional
    public String sendMessage(final Long sessionId, final String message) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(message, "message must not be null");

        final ChatSession chatSession = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (chatSession.isExpired(LocalDateTime.now(), EXPIRATION_MINUTES)) {
            throw new SessionExpiredException(sessionId);
        }

        final List<ChatMessage> recentMessages = chatMessageRepository.findTop20BySessionOrderByCreatedAtDesc(
                chatSession);

        final List<Message> promptMessages = buildPromptMessages(recentMessages, message);

        final String response = chatClient.prompt(new Prompt(promptMessages))
                .call()
                .content();

        chatMessageRepository.save(new ChatMessage(chatSession, MessageRole.USER, message));
        chatMessageRepository.save(new ChatMessage(chatSession, MessageRole.ASSISTANT, response));

        return response;
    }

    private List<Message> buildPromptMessages(
            final List<ChatMessage> recentMessages,
            final String newMessage) {
        final List<Message> messages = new ArrayList<>();

        final List<ChatMessage> chronological = new ArrayList<>(recentMessages);
        java.util.Collections.reverse(chronological);

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
