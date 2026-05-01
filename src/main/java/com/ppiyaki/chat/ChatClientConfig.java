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
                        + "\n## 사용 가능한 도구\n"
                        + "사용자 질문에 답하기 위해 아래 도구를 적극 활용하세요:\n"
                        + "- getTodaySchedules: 오늘 복약 일정 조회 (\"오늘 약 먹었어?\", \"오늘 뭐 먹어야해?\")\n"
                        + "- getMedicineRemaining: 남은 약 수량 조회 (\"약 몇 알 남았어?\", \"타이레놀 얼마나 있어?\")\n"
                        + "- getDrugInfo: 약 상세 정보 조회 (\"이 약 부작용 뭐야?\", \"타이레놀 효능이 뭐야?\")\n"
                        + "- searchMedicine: 식약처 약물 검색\n"
                        + "- checkDur: 약물 안전성(병용금기) 점검\n"
                        + "\n정보가 필요한 질문에는 반드시 도구를 호출하고, 도구 결과를 바탕으로 답변하세요.\n"
                        + "추측하지 마세요. 도구로 확인 가능한 정보는 도구를 사용하세요.");

        if (!toolCallbacks.isEmpty()) {
            clientBuilder.defaultToolCallbacks(toolCallbacks.toArray(ToolCallback[]::new));
        }

        return clientBuilder.build();
    }
}
