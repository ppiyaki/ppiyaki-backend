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
                        () -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        if (!chatSession.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_ACCESS_DENIED);
        }
        // 세션 만료 검사는 여기서 에러를 던지지 않고, loadSessionAndBuildPrompt에서 Transparent Fallback으로 처리함.
    }

    @Transactional(readOnly = true)
    public List<Message> loadSessionAndBuildPrompt(
            final Long userId, final Long sessionId, final String message) {
        final ChatSession chatSession = chatSessionRepository.findById(sessionId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        if (!chatSession.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_ACCESS_DENIED);
        }

        final List<ChatMessage> recentMessages;
        String promptMessage = message;

        if (chatSession.isExpired(LocalDateTime.now(), EXPIRATION_MINUTES)) {
            // Transparent Fallback: 에러를 던지지 않고 히스토리를 비움
            recentMessages = Collections.emptyList();
            promptMessage = "[시스템 알림: 마지막 대화 이후 5분이 경과되어 이전 대화 내용이 초기화되었습니다. "
                    + "만약 사용자가 이전 대화의 문맥(예: '그 약', '아까 말한 거')을 가리키며 질문한다면, "
                    + "친절하게 '시간이 좀 지나서 이전 대화를 깜빡했어요. 어떤 약인지 다시 말씀해 주시겠어요?' 라고 안내해 주세요.]\n\n"
                    + "사용자: " + message;
        } else {
            recentMessages = chatMessageRepository.findTop20BySessionOrderByCreatedAtDescIdDesc(chatSession);
        }

        return buildPromptMessages(recentMessages, promptMessage);
    }

    @Transactional
    public void saveMessages(final Long sessionId, final String userMessage, final String assistantResponse) {
        final ChatSession chatSession = chatSessionRepository.findById(sessionId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND));

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
