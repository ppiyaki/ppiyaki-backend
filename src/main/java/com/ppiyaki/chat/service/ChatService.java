package com.ppiyaki.chat.service;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;

    public String chat(final String message) {
        Objects.requireNonNull(message, "message must not be null");
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
