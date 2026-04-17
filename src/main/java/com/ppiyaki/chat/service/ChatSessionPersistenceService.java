package com.ppiyaki.chat.service;

import com.ppiyaki.chat.domain.ChatMessage;
import com.ppiyaki.chat.domain.ChatSession;
import com.ppiyaki.chat.domain.MessageRole;
import com.ppiyaki.chat.repository.ChatMessageRepository;
import com.ppiyaki.chat.repository.ChatSessionRepository;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        return chatSessionRepository.save(ChatSession.create(userId));
    }

    @Transactional(readOnly = true)
    public void validateSession(final Long userId, final Long sessionId) {
        final ChatSession chatSession = chatSessionRepository.findById(sessionId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND, "세션을 찾을 수 없습니다: " + sessionId));

        if (!chatSession.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_ACCESS_DENIED, "세션에 대한 접근 권한이 없습니다: " + sessionId);
        }

        if (chatSession.isExpired(LocalDateTime.now(), EXPIRATION_MINUTES)) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_EXPIRED, "세션이 만료되었습니다. 새 세션을 생성해주세요: " + sessionId);
        }
    }

    @Transactional(readOnly = true)
    public List<Message> loadSessionAndBuildPrompt(
            final Long userId, final Long sessionId, final String message) {
        final ChatSession chatSession = chatSessionRepository.findById(sessionId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND, "세션을 찾을 수 없습니다: " + sessionId));

        if (!chatSession.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_ACCESS_DENIED, "세션에 대한 접근 권한이 없습니다: " + sessionId);
        }

        if (chatSession.isExpired(LocalDateTime.now(), EXPIRATION_MINUTES)) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_EXPIRED, "세션이 만료되었습니다. 새 세션을 생성해주세요: " + sessionId);
        }

        final List<ChatMessage> recentMessages = chatMessageRepository.findTop20BySessionOrderByCreatedAtDescIdDesc(
                chatSession);

        return buildPromptMessages(recentMessages, message);
    }

    @Transactional
    public void saveMessages(final Long sessionId, final String userMessage, final String assistantResponse) {
        final ChatSession chatSession = chatSessionRepository.findById(sessionId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND, "세션을 찾을 수 없습니다: " + sessionId));

        chatMessageRepository.save(new ChatMessage(chatSession, MessageRole.USER, userMessage));
        chatMessageRepository.save(new ChatMessage(chatSession, MessageRole.ASSISTANT, assistantResponse));
        chatSession.touch();
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
