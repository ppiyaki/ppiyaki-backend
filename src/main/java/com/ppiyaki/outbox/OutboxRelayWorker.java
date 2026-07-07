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
