package com.ppiyaki.chat;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(
            final ChatClient.Builder builder,
            final List<ToolCallback> toolCallbacks
    ) {
        final ChatClient.Builder clientBuilder = builder
                .defaultSystem("당신은 복약 관리 서비스 '삐약이'의 AI 도우미입니다.\n"
                        + "사용자는 어르신(시니어)입니다.\n"
                        + "- 최대한 쉽고 간결한 용어를 사용하세요.\n"
                        + "- 답변은 짧고 핵심만 전달하세요.\n"
                        + "- 의학 전문 용어 대신 일상 표현을 사용하세요.\n"
                        + "- 복약 관련 질문에 친절하고 정확하게 답변해 주세요.\n"
                        + "- 약물 검색이나 DUR 점검이 필요하면 제공된 도구를 사용하세요.");

        if (!toolCallbacks.isEmpty()) {
            clientBuilder.defaultToolCallbacks(toolCallbacks.toArray(ToolCallback[]::new));
        }

        return clientBuilder.build();
    }
}
