package com.ppiyaki.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox relay의 트랜잭션 작업을 담당하는 워커. {@link MedicationCompleteOutboxRelay}의
 * {@code @Scheduled} 진입점과 {@code @Transactional} 처리 로직을 별도 빈으로 분리해,
 * 진입점이 이 빈을 일반 주입으로 호출하는 것만으로 트랜잭션 프록시가 적용되게 한다.
 *
 * <p><b>트랜잭션 경계</b>: {@link #processBatch()}가 배치 단위의 트랜잭션을 열고,
 * 그 안에서 알림함 record 저장과 outbox 상태 변경까지만 수행한다. 외부 네트워크 호출인
 * FCM 발송은 여기서 하지 않고 {@link PushCommand} 목록으로 반환만 한다.
 * dispatch는 poison 메시지 격리를 위해 REQUIRES_NEW의 메시지 단위 별도 트랜잭션에서
 * 수행된다({@link MedicationCompleteDispatcher#dispatchCompletedSlots} 참고).
 */
@Component
public class OutboxRelayWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayWorker.class);

    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final MedicationCompleteDispatcher dispatcher;
    private final DeviceTokenRepository deviceTokenRepository;
    private final Clock clock;
    private final int batchSize;

    public OutboxRelayWorker(
            final OutboxMessageRepository outboxMessageRepository,
            final ObjectMapper objectMapper,
            final MedicationCompleteDispatcher dispatcher,
            final DeviceTokenRepository deviceTokenRepository,
            final Clock clock,
            @Value("${outbox.relay.batch-size:50}") final int batchSize
    ) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.deviceTokenRepository = deviceTokenRepository;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /**
     * PENDING 메시지 한 배치를 조회해 처리한다.
     * 자기 이벤트 타입({@link OutboxService#MEDICATION_COMPLETE})의 메시지만 처리한다.
     *
     * <p><b>poison-batch 격리</b>: dispatch({@code dispatchCompletedSlots})는 REQUIRES_NEW로
     * 메시지 단위의 별도 트랜잭션에서 수행된다. dispatch가 실패해도 그 내부 트랜잭션만 롤백되고
     * 이 배치 트랜잭션은 rollback-only로 마킹되지 않으므로, catch 블록의 recordFailure
     * (attempts 증가/백오프/데드레터)와 같은 배치의 다른 메시지의 markProcessed가 정상 커밋된다.
     * dispatch가 REQUIRED로 이 트랜잭션에 합류하면 실패 1건이 참여 트랜잭션을 rollback-only로
     * 만들어(UnexpectedRollbackException) 배치 전체가 롤백되고 attempts도 오르지 않는다.
     *
     * <p>개별 메시지 실패는 recordFailure로 기록만 하고 예외를 전파하지 않는다
     * (한 건 실패가 배치 전체를 롤백시키지 않게). 상태 변경은 JPA dirty checking으로 반영된다.
     *
     * <p>트랜잭션 안에서는 알림함 record 저장 + outbox 상태 변경까지만 수행하고,
     * FCM 발송용 {@link PushCommand}는 모아서 반환만 한다(발송은 relay가 커밋 후 수행).
     *
     * @return 커밋 이후 발송할 푸시 명령 목록 (처리할 메시지가 없으면 빈 리스트)
     */
    @Transactional
    public List<PushCommand> processBatch() {
        final List<OutboxMessage> messages = outboxMessageRepository
                .findClaimable(OutboxService.MEDICATION_COMPLETE, LocalDateTime.now(clock), batchSize);
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
        log.info("Outbox relay batch done: polled={}, processed={}, failed={}, pushCommands={}",
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
     * 발송 결과 invalid로 판명된 device token들을 별도의 짧은 트랜잭션에서 비활성화한다.
     * 트랜잭션 밖 발송 결과이므로 detach된 엔티티의 dirty checking에 의존하지 않고
     * id로 재조회해 비활성화한다.
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
