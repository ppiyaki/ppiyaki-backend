package com.ppiyaki.chat.controller;

import com.ppiyaki.chat.controller.dto.ChatMessageRequest;
import com.ppiyaki.chat.controller.dto.ChatSessionResponse;
import com.ppiyaki.chat.domain.ChatSession;
import com.ppiyaki.chat.service.ChatSessionPersistenceService;
import com.ppiyaki.chat.service.ChatSessionService;
import com.ppiyaki.chat.service.SttService;
import com.ppiyaki.chat.service.TtsService;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionPersistenceService persistenceService;
    private final ChatSessionService chatSessionService;
    private final SttService sttService;
    private final TtsService ttsService;

    @PostMapping
    public ResponseEntity<ChatSessionResponse> createSession(
            @AuthenticationPrincipal final Long userId) {
        final ChatSession chatSession = persistenceService.createSession(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ChatSessionResponse(chatSession.getId()));
    }

    @PostMapping(value = "/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long sessionId,
            @Valid @RequestBody final ChatMessageRequest chatMessageRequest) {
        return chatSessionService.sendMessageStream(userId, sessionId, chatMessageRequest.message());
    }

    @PostMapping(value = "/{sessionId}/voice-messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = {
            MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public SseEmitter sendVoiceMessage(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long sessionId,
            @RequestParam("file") final MultipartFile file,
            @RequestParam(value = "language", defaultValue = "ko") final String language) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.CHAT_VOICE_FILE_EMPTY);
        }

        persistenceService.validateSession(userId, sessionId);
        final String transcribedText = sttService.transcribe(file.getResource(), language);
        return chatSessionService.sendVoiceMessageStream(userId, sessionId, transcribedText, ttsService);
    }
}
