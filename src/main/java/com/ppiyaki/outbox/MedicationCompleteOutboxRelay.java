package com.ppiyaki.outbox;

import com.ppiyaki.infrastructure.messaging.fcm.PushSendResult;
import com.ppiyaki.infrastructure.messaging.fcm.PushSender;
import com.ppiyaki.notification.service.PushCommand;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MedicationCompleteOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(MedicationCompleteOutboxRelay.class);

    private final OutboxRelayWorker worker;
    private final PushSender pushSender;

    public MedicationCompleteOutboxRelay(final OutboxRelayWorker worker, final PushSender pushSender) {
        this.worker = worker;
        this.pushSender = pushSender;
    }

    @Scheduled(
            fixedDelayString = "${outbox.relay.fixed-delay-ms:2000}",
            initialDelayString = "${outbox.relay.initial-delay-ms:5000}"
    )
    public void poll() {

        final List<PushCommand> pushCommands = worker.processBatch();
        sendPushes(pushCommands);
    }

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
            worker.deactivateInvalidTokens(invalidTokenIds);
        } catch (final Exception e) {
            log.warn("Failed to deactivate invalid device tokens {}", invalidTokenIds, e);
        }
    }
}
