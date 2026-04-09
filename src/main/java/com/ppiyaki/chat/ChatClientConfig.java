package com.ppiyaki.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(final ChatClient.Builder builder) {
        return builder
                .defaultSystem("당신은 복약 관리 서비스 '삐약이'의 AI 도우미입니다. "
                        + "시니어 사용자의 복약 관련 질문에 친절하고 정확하게 답변해 주세요.")
                .build();
    }
}
