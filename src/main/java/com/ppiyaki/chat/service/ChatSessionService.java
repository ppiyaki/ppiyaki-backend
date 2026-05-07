package com.ppiyaki.chat.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final long SSE_TIMEOUT = 60_000L;

    /**
     * 사진 메시지 전용 시스템 프롬프트. spec chat-photo-messages.md §5-3.
     * 약/비약 자동 분기를 LLM 자율 결정으로 위임.
     */
    private static final String PHOTO_SYSTEM_PROMPT = """
            당신은 시니어를 돕는 복약 관리 비서입니다.
            사용자가 사진을 보내면 다음 규칙을 따르세요:

            1. 사진을 보고 약(알약/캡슐/약병/약 봉투/처방전 등)인지 먼저 판단하세요.

            2. 약(특히 알약/캡슐)이면:
               - 첫 문장에 사진 속 약의 외형을 짧게 묘사하세요 (각인·색·모양·분할선·크기). 후속 질의에서 컨텍스트로 활용됩니다.
               - 약명을 추측하지 말고, 추출한 외형으로 즉시 `identifyPillByAppearance` 도구를 호출하세요.
                 * 파라미터 매핑 규칙(중요):
                   - drugShape: 한국어 enum 그대로 — `원형` / `장방형` / `타원형` / `삼각형` / `사각형` / `마름모형` / `반원형` 등
                   - colorClass1: `하양` / `노랑` / `빨강` / `파랑` / `초록` / `주황` / `분홍` / `자주` / `갈색` / `검정`
                     사용자 묘사 "흰색"은 `하양`으로, "검은색"은 `검정`으로 변환해 전달하세요.
                   - lineFront: `+형` / `-형`. 분할선 없으면 null.
                   - 불확실한 필드는 null로 두고 호출 — 일부 필드만으로도 검색 가능합니다.
               - 도구 응답으로 받은 후보(0~10건)에 따라:
                 * 0건 — "외형으로 일치하는 약이 없습니다. 각인이 보이도록 다시 찍어 주시거나 약 봉투/처방전을 함께 보여 주세요."
                 * 1건 — 단일 정답으로 응답하고, 필요하면 `getDrugInfo`/`checkDur`로 효능·주의사항을 보강하세요.
                 * 2건 이상 — 후보 약명을 짧은 목록으로 제시하고 "각인/뒷면/색이 더 보이게 다시 찍어 주시면 좁힐 수 있어요."로 follow-up.
               - 후보를 받기 전에 약명을 단정하지 마세요. 추측 금지.

            3. 약이 아니면:
               - 약 관련 도구(searchMedicine, getDrugInfo, checkDur, identifyPillByAppearance)는 호출하지 마세요.
               - 사진 속 대상을 짧게 묘사하고, 사용자 질문에 자유롭게 답하세요.
               - 의료·건강 조언이 필요한 영역(예: 발진·상처 등)이면 "사진만으로는 정확한 판단이 어렵습니다. 의료기관·약사 상담을 권합니다." 한계를 명시하세요.

            4. 진단·처방 같은 의료 결정은 하지 마세요.
            5. 한국어 존댓말, 시니어가 이해하기 쉬운 짧은 문장으로.
            """;

    private static final String DEFAULT_PHOTO_PROMPT = "이 사진 속 약을 식별해줘";

    private final ChatSessionPersistenceService persistenceService;
    private final ChatClient chatClient;
    private final Executor chatStreamExecutor;

    public SseEmitter sendMessageStream(final Long userId, final Long sessionId, final String message) {
        final List<Message> promptMessages = persistenceService.loadSessionAndBuildPrompt(userId, sessionId, message);

        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        final StringBuilder fullResponse = new StringBuilder();

        chatStreamExecutor.execute(() -> {
            final Disposable subscription = chatClient.prompt(new Prompt(promptMessages))
                    .toolContext(Map.of("userId", userId))
                    .stream()
                    .content()
                    .doOnNext(token -> {
                        try {
                            fullResponse.append(token);
                            emitter.send(SseEmitter.event().data(token));
                        } catch (final Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            persistenceService.saveMessages(sessionId, message, fullResponse.toString());
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                        } catch (final Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(emitter::completeWithError)
                    .subscribe();

            emitter.onTimeout(subscription::dispose);
            emitter.onCompletion(subscription::dispose);
            emitter.onError(error -> subscription.dispose());
        });

        return emitter;
    }

    public SseEmitter sendVoiceMessageStream(
            final Long userId,
            final Long sessionId,
            final String transcribedText,
            final TtsService ttsService) {
        final List<Message> promptMessages = persistenceService.loadSessionAndBuildPrompt(userId, sessionId,
                transcribedText);

        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        final StringBuilder fullResponse = new StringBuilder();
        final SentenceBuffer sentenceBuffer = new SentenceBuffer();

        chatStreamExecutor.execute(() -> {
            final Disposable subscription = chatClient.prompt(new Prompt(promptMessages))
                    .toolContext(Map.of("userId", userId))
                    .stream()
                    .content()
                    .doOnNext(token -> {
                        try {
                            fullResponse.append(token);
                            sentenceBuffer.append(token)
                                    .ifPresent(sentence -> sendVoiceEvent(emitter, ttsService, sentence));
                        } catch (final Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            sentenceBuffer.flush()
                                    .ifPresent(sentence -> sendVoiceEvent(emitter, ttsService, sentence));
                            persistenceService.saveMessages(sessionId, transcribedText, fullResponse.toString());
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                        } catch (final Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(emitter::completeWithError)
                    .subscribe();

            emitter.onTimeout(subscription::dispose);
            emitter.onCompletion(subscription::dispose);
            emitter.onError(error -> subscription.dispose());
        });

        return emitter;
    }

    /**
     * 사진 첨부 채팅 메시지 SSE 스트림. spec chat-photo-messages.md §5-4.
     * - 시스템 프롬프트로 약/비약 자동 분기 + 묘사 포함 응답 유도
     * - 기존 세션 히스토리 + 새 user message(텍스트 + image media)
     * - 도구 chain: ToolContext에 userId 전달 (PR #232 패턴)
     * - 메시지 히스토리: USER에 placeholder만 저장(이미지 자체 X)
     */
    public SseEmitter sendPhotoMessageStream(
            final Long userId,
            final Long sessionId,
            final byte[] imageBytes,
            final String mediaType,
            final String userMessageText) {
        final String effectiveText = (userMessageText == null || userMessageText.isBlank())
                ? DEFAULT_PHOTO_PROMPT : userMessageText;
        final String placeholder = "[이미지 첨부] " + effectiveText;

        // 기존 세션 히스토리는 placeholder만 들어가지만, 새 turn은 실제 이미지가 LLM에 전달돼야
        // 분기·묘사 가능. 따라서 history 기반 prompt를 빌드한 뒤 마지막 user turn을
        // 이미지 첨부 UserMessage로 교체한다.
        final List<Message> historyPrompt = persistenceService.loadSessionAndBuildPrompt(
                userId, sessionId, placeholder);
        final List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(PHOTO_SYSTEM_PROMPT));
        for (int i = 0; i < historyPrompt.size() - 1; i++) {
            promptMessages.add(historyPrompt.get(i));
        }
        promptMessages.add(UserMessage.builder()
                .text(effectiveText)
                .media(new Media(MimeType.valueOf(mediaType), new ByteArrayResource(imageBytes)))
                .build());

        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        final StringBuilder fullResponse = new StringBuilder();

        chatStreamExecutor.execute(() -> {
            final Disposable subscription = chatClient.prompt(new Prompt(promptMessages))
                    .toolContext(Map.of("userId", userId))
                    .stream()
                    .content()
                    .doOnNext(token -> {
                        try {
                            fullResponse.append(token);
                            emitter.send(SseEmitter.event().data(token));
                        } catch (final Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            persistenceService.saveMessages(sessionId, placeholder, fullResponse.toString());
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                        } catch (final Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(emitter::completeWithError)
                    .subscribe();

            emitter.onTimeout(subscription::dispose);
            emitter.onCompletion(subscription::dispose);
            emitter.onError(error -> subscription.dispose());
        });

        return emitter;
    }

    private void sendVoiceEvent(final SseEmitter emitter, final TtsService ttsService, final String sentence) {
        try {
            final byte[] audioBytes = ttsService.synthesize(sentence);
            final String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);
            final String json = "{\"text\":\"" + escapeJson(sentence)
                    + "\",\"audio\":\"" + audioBase64 + "\"}";
            emitter.send(SseEmitter.event().data(json));
        } catch (final Exception e) {
            emitter.completeWithError(e);
        }
    }

    private String escapeJson(final String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
