package com.ppiyaki.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppiyaki.infrastructure.messaging.fcm.PushSendResult;
import com.ppiyaki.infrastructure.messaging.fcm.PushSender;
import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.notification.DeviceToken;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
import com.ppiyaki.notification.service.MedicationCompleteDispatcher;
import com.ppiyaki.notification.service.PushCommand;
import com.ppiyaki.outbox.repository.OutboxMessageRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복약 완료 알림 Outbox relay. PENDING 메시지를 주기적으로 클레임해
 * {@link MedicationCompleteDispatcher}로 알림을 발행한다.
 *
 * <p>트랜잭션 경계: {@code findClaimable}이 FOR UPDATE SKIP LOCKED이므로
 * claim + dispatch + 상태 변경(markProcessed/recordFailure)이 <b>한 트랜잭션</b> 안에 있어야
 * 처리 중 row lock이 유지되어 멀티 인스턴스 중복 처리가 방지된다.
 * {@code @Scheduled} 진입점에 {@code @Transactional}을 직접 걸면 self-invocation으로
 * 프록시를 우회할 수 있어, 진입점 {@link #poll()}과 트랜잭션 메서드 {@link #processBatch()}를 분리하고
 * {@code @Lazy} self 주입 프록시를 경유해 호출한다.
 *
 * <p><b>내구 record vs best-effort 푸시</b>: 트랜잭션 안에서는 알림함 record 저장과 outbox 상태
 * 변경까지만 수행하고, 외부 네트워크 호출인 FCM 발송은 {@link #processBatch()}가 반환한
 * {@link PushCommand} 목록을 {@link #poll()}이 <b>커밋 이후</b>(processBatch 반환 이후) 트랜잭션 밖에서
 * 실행한다. 외부 호출이 DB 커넥션/락을 점유하지 않게 하기 위함이다. 푸시 발송 실패는 로깅만 하고
 * 삼킨다 — record는 이미 내구적으로 저장·커밋됐고 메시지도 PROCESSED이므로 outbox 재시도 대상이
 * 아니다(outbox 재시도는 record 저장 실패에 대한 것, 푸시는 best-effort 계층).
 */
@Component
public class MedicationCompleteOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(MedicationCompleteOutboxRelay.class);

    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final MedicationCompleteDispatcher dispatcher;
    private final PushSender pushSender;
    private final DeviceTokenRepository deviceTokenRepository;
    private final Clock clock;
    private final int batchSize;
    private final MedicationCompleteOutboxRelay self;

    public MedicationCompleteOutboxRelay(
            final OutboxMessageRepository outboxMessageRepository,
            final ObjectMapper objectMapper,
            final MedicationCompleteDispatcher dispatcher,
            final PushSender pushSender,
            final DeviceTokenRepository deviceTokenRepository,
            final Clock clock,
            @Value("${outbox.relay.batch-size:50}") final int batchSize,
            @Lazy final MedicationCompleteOutboxRelay self
    ) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.pushSender = pushSender;
        this.deviceTokenRepository = deviceTokenRepository;
        this.clock = clock;
        this.batchSize = batchSize;
        this.self = self;
    }

    @Scheduled(
            fixedDelayString = "${outbox.relay.fixed-delay-ms:2000}",
            initialDelayString = "${outbox.relay.initial-delay-ms:5000}"
    )
    public void poll() {
        // self(프록시) 경유 호출로 processBatch의 @Transactional이 실제 적용되게 한다.
        // processBatch가 반환한 시점에는 record 저장·outbox 상태 변경 트랜잭션이 이미 커밋됐다.
        final List<PushCommand> pushCommands = self.processBatch();
        // FCM 발송은 커밋 이후, 트랜잭션·DB 커넥션 밖에서 best-effort로 수행한다.
        sendPushes(pushCommands);
    }

    /**
     * PENDING 메시지 한 배치를 클레임해 처리한다. 이 메서드의 트랜잭션이 claim 락의 수명이다.
     *
     * <p>개별 메시지 실패는 recordFailure로 기록만 하고 예외를 전파하지 않는다
     * (한 건 실패가 배치 전체를 롤백시키지 않게). 상태 변경은 JPA dirty checking으로 반영된다.
     *
     * <p>트랜잭션 안에서는 알림함 record 저장 + outbox 상태 변경까지만 수행하고,
     * FCM 발송용 {@link PushCommand}는 모아서 반환만 한다(실행은 {@link #poll()}이 커밋 후 수행).
     *
     * @return 커밋 이후 발송할 푸시 명령 목록 (처리할 메시지가 없으면 빈 리스트)
     */
    @Transactional
    public List<PushCommand> processBatch() {
        final List<OutboxMessage> messages = outboxMessageRepository
                .findClaimable(LocalDateTime.now(clock), batchSize);
        if (messages.isEmpty()) {
            return List.of();
        }

        final List<PushCommand> pushCommands = new ArrayList<>();
        int processed = 0;
        int failed = 0;
        for (final OutboxMessage message : messages) {
            try {
                pushCommands.addAll(dispatch(message));
                message.markProcessed(LocalDateTime.now(clock));
                processed++;
            } catch (final Exception e) {
                log.warn("Outbox message processing failed (id={}, eventType={}, attempts={})",
                        message.getId(), message.getEventType(), message.getAttempts(), e);
                message.recordFailure(summarize(e), LocalDateTime.now(clock));
                failed++;
            }
        }
        log.info("Outbox relay batch done: claimed={}, processed={}, failed={}, pushCommands={}",
                messages.size(), processed, failed, pushCommands.size());
        return pushCommands;
    }

    private List<PushCommand> dispatch(final OutboxMessage message) throws Exception {
        if (OutboxService.MEDICATION_COMPLETE.equals(message.getEventType())) {
            final MedicationTakenEvent event = objectMapper
                    .readValue(message.getPayload(), MedicationTakenEvent.class);
            return dispatcher.dispatchCompletedSlots(event.seniorId(), event.targetDate());
        }
        throw new IllegalStateException("Unsupported outbox eventType: " + message.getEventType());
    }

    /**
     * 커밋 이후 트랜잭션 밖에서 FCM 푸시를 best-effort로 발송한다.
     *
     * <p>발송 실패(예외 포함)는 로깅만 하고 삼킨다 — 알림함 record는 이미 내구적으로 커밋됐고,
     * 커밋 후 발송이므로 실패해도 outbox 메시지를 실패 처리하지 않는다(재시도는 이번 범위 밖).
     * invalid로 판명된 토큰은 모아서 별도의 짧은 트랜잭션으로 비활성화한다.
     */
    private void sendPushes(final List<PushCommand> pushCommands) {
        if (pushCommands.isEmpty()) {
            return;
        }
        final List<Long> invalidTokenIds = new ArrayList<>();
        for (final PushCommand command : pushCommands) {
            try {
                final PushSendResult result = pushSender.send(command.deviceToken(), command.payload());
                if (result.tokenInvalid()) {
                    invalidTokenIds.add(command.deviceTokenId());
                } else if (!result.success()) {
                    log.warn("Best-effort push send failed (caregiver={}, tokenId={}): {}",
                            command.caregiverId(), command.deviceTokenId(), result.errorMessage());
                }
            } catch (final Exception e) {
                log.warn("Best-effort push send threw (caregiver={}, tokenId={})",
                        command.caregiverId(), command.deviceTokenId(), e);
            }
        }
        if (invalidTokenIds.isEmpty()) {
            return;
        }
        try {
            // 트랜잭션 밖 발송 결과이므로 detach된 엔티티의 dirty checking에 의존하지 않고,
            // self(프록시) 경유의 짧은 새 트랜잭션에서 id로 재조회해 비활성화한다.
            self.deactivateInvalidTokens(invalidTokenIds);
        } catch (final Exception e) {
            log.warn("Failed to deactivate invalid device tokens {}", invalidTokenIds, e);
        }
    }

    /**
     * 발송 결과 invalid로 판명된 device token들을 별도의 짧은 트랜잭션에서 비활성화한다.
     * {@link #poll()}에서 self 프록시 경유로 호출되어 @Transactional이 실제 적용된다.
     */
    @Transactional
    public void deactivateInvalidTokens(final List<Long> deviceTokenIds) {
        final List<DeviceToken> tokens = deviceTokenRepository.findAllById(deviceTokenIds);
        tokens.forEach(DeviceToken::deactivate);
        log.info("Deactivated {} invalid device token(s): {}", tokens.size(), deviceTokenIds);
    }

    private static String summarize(final Exception e) {
        final String summary = e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return summary.length() <= MAX_ERROR_LENGTH ? summary : summary.substring(0, MAX_ERROR_LENGTH);
    }
}
