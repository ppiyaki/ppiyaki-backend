package com.ppiyaki.chat.controller;

import com.ppiyaki.chat.controller.dto.ChatMessageRequest;
import com.ppiyaki.chat.controller.dto.ChatMessageResponse;
import com.ppiyaki.chat.controller.dto.ChatSessionResponse;
import com.ppiyaki.chat.domain.ChatSession;
import com.ppiyaki.chat.service.ChatSessionService;
import com.ppiyaki.chat.service.SessionAccessDeniedException;
import com.ppiyaki.chat.service.SessionExpiredException;
import com.ppiyaki.chat.service.SessionNotFoundException;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @PostMapping
    public ResponseEntity<ChatSessionResponse> createSession(
            @AuthenticationPrincipal final Long userId) {
        final ChatSession chatSession = chatSessionService.createSession(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ChatSessionResponse(chatSession.getId()));
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long sessionId,
            @Valid @RequestBody final ChatMessageRequest chatMessageRequest) {
        final String response = chatSessionService.sendMessage(userId, sessionId, chatMessageRequest.message());
        return ResponseEntity.ok(new ChatMessageResponse(response));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSessionNotFound(final SessionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<Map<String, String>> handleSessionExpired(final SessionExpiredException exception) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(SessionAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleSessionAccessDenied(
            final SessionAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", exception.getMessage()));
    }
}
