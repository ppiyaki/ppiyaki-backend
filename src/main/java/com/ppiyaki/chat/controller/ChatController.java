package com.ppiyaki.chat.controller;

import com.ppiyaki.chat.controller.dto.ChatRequest;
import com.ppiyaki.chat.controller.dto.ChatResponse;
import com.ppiyaki.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody @Valid final ChatRequest chatRequest) {
        final String message = chatService.chat(chatRequest.message());
        return ResponseEntity.ok(new ChatResponse(message));
    }
}
