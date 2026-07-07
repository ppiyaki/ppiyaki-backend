package com.ppiyaki.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.notification.service.MedicationCompleteDispatcher;
import com.ppiyaki.outbox.repository.OutboxMessageRepository;
import java.time.Clock;
import java.time.LocalDateTime;
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
 */
@Component
public class MedicationCompleteOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(MedicationCompleteOutboxRelay.class);

    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final MedicationCompleteDispatcher dispatcher;
    private final Clock clock;
    private final int batchSize;
    private final MedicationCompleteOutboxRelay self;

    public MedicationCompleteOutboxRelay(
            final OutboxMessageRepository outboxMessageRepository,
            final ObjectMapper objectMapper,
            final MedicationCompleteDispatcher dispatcher,
            final Clock clock,
            @Value("${outbox.relay.batch-size:50}") final int batchSize,
            @Lazy final MedicationCompleteOutboxRelay self
    ) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
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
        self.processBatch();
    }

    /**
     * PENDING 메시지 한 배치를 클레임해 처리한다. 이 메서드의 트랜잭션이 claim 락의 수명이다.
     *
     * <p>개별 메시지 실패는 recordFailure로 기록만 하고 예외를 전파하지 않는다
     * (한 건 실패가 배치 전체를 롤백시키지 않게). 상태 변경은 JPA dirty checking으로 반영된다.
     */
    @Transactional
    public void processBatch() {
        final List<OutboxMessage> messages = outboxMessageRepository
                .findClaimable(LocalDateTime.now(clock), batchSize);
        if (messages.isEmpty()) {
            return;
        }

        int processed = 0;
        int failed = 0;
        for (final OutboxMessage message : messages) {
            try {
                dispatch(message);
                message.markProcessed(LocalDateTime.now(clock));
                processed++;
            } catch (final Exception e) {
                log.warn("Outbox message processing failed (id={}, eventType={}, attempts={})",
                        message.getId(), message.getEventType(), message.getAttempts(), e);
                message.recordFailure(summarize(e), LocalDateTime.now(clock));
                failed++;
            }
        }
        log.info("Outbox relay batch done: claimed={}, processed={}, failed={}",
                messages.size(), processed, failed);
    }

    private void dispatch(final OutboxMessage message) throws Exception {
        if (OutboxService.MEDICATION_COMPLETE.equals(message.getEventType())) {
            final MedicationTakenEvent event = objectMapper
                    .readValue(message.getPayload(), MedicationTakenEvent.class);
            dispatcher.dispatchCompletedSlots(event.seniorId(), event.targetDate());
            return;
        }
        throw new IllegalStateException("Unsupported outbox eventType: " + message.getEventType());
    }

    private static String summarize(final Exception e) {
        final String summary = e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return summary.length() <= MAX_ERROR_LENGTH ? summary : summary.substring(0, MAX_ERROR_LENGTH);
    }
}
