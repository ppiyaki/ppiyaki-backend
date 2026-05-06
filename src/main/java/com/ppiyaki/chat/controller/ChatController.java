package com.ppiyaki.chat.controller;

import com.ppiyaki.chat.controller.dto.ChatMessageRequest;
import com.ppiyaki.chat.domain.ChatSession;
import com.ppiyaki.chat.service.ChatSessionPersistenceService;
import com.ppiyaki.chat.service.ChatSessionService;
import com.ppiyaki.chat.service.SttService;
import com.ppiyaki.chat.service.TtsService;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 단발(single-turn) 채팅 API.
 * 세션 ID를 클라이언트가 관리하지 않고, 서버가 임시 세션을 자동 생성한다.
 * spec: docs/features/chat-quick-messages.md
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatSessionPersistenceService persistenceService;
    private final ChatSessionService chatSessionService;
    private final SttService sttService;
    private final TtsService ttsService;

    public ChatController(
            final ChatSessionPersistenceService persistenceService,
            final ChatSessionService chatSessionService,
            final SttService sttService,
            final TtsService ttsService
    ) {
        this.persistenceService = persistenceService;
        this.chatSessionService = chatSessionService;
        this.sttService = sttService;
        this.ttsService = ttsService;
    }

    @PostMapping("/messages")
    public SseEmitter quickMessage(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final ChatMessageRequest request) {
        final ChatSession session = persistenceService.createSession(userId);
        return chatSessionService.sendMessageStream(userId, session.getId(), request.message());
    }

    @PostMapping(value = "/voice-messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SseEmitter quickVoiceMessage(
            @AuthenticationPrincipal final Long userId,
            @RequestParam("file") final MultipartFile file,
            @RequestParam(value = "language", defaultValue = "ko") final String language) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.CHAT_VOICE_FILE_EMPTY);
        }
        final String transcribedText = sttService.transcribe(file.getResource(), language);
        final ChatSession session = persistenceService.createSession(userId);
        return chatSessionService.sendVoiceMessageStream(userId, session.getId(), transcribedText, ttsService);
    }
}
