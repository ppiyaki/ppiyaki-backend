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

/**
 * 복약 완료 알림 Outbox relay. PENDING 메시지를 주기적으로 폴링해 알림을 발행한다.
 *
 * <p>구조: {@code @Scheduled} 진입점(이 클래스)과 {@code @Transactional} 배치 처리
 * ({@link OutboxRelayWorker})를 별도 빈으로 분리했다. 이 클래스는 트랜잭션을 열지 않고
 * 워커를 일반 주입으로 호출하기만 한다.
 *
 * <p><b>내구 record vs best-effort 푸시</b>: 트랜잭션 안에서는 알림함 record 저장과 outbox 상태
 * 변경까지만 수행하고({@link OutboxRelayWorker#processBatch}), 외부 네트워크 호출인 FCM 발송은
 * 워커가 반환한 {@link PushCommand} 목록을 {@link #poll()}이 <b>커밋 이후</b>(processBatch 반환 이후)
 * 트랜잭션 밖에서 실행한다. 외부 호출이 DB 커넥션/락을 점유하지 않게 하기 위함이다.
 * 푸시 발송 실패는 로깅만 하고 삼킨다. record는 이미 내구적으로 저장/커밋됐고 메시지도
 * PROCESSED이므로 outbox 재시도 대상이 아니다(outbox 재시도는 record 저장 실패에 대한 것,
 * 푸시는 best-effort 계층).
 */
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
        // processBatch가 반환한 시점에는 record 저장, outbox 상태 변경 트랜잭션이 이미 커밋됐다.
        final List<PushCommand> pushCommands = worker.processBatch();
        // FCM 발송은 커밋 이후, 트랜잭션과 DB 커넥션 밖에서 best-effort로 수행한다.
        sendPushes(pushCommands);
    }

    /**
     * 커밋 이후 트랜잭션 밖에서 FCM 푸시를 best-effort로 발송한다.
     *
     * <p>발송 실패(예외 포함)는 로깅만 하고 삼킨다. 알림함 record는 이미 내구적으로 커밋됐고,
     * 커밋 후 발송이므로 실패해도 outbox 메시지를 실패 처리하지 않는다(재시도는 이번 범위 밖).
     * invalid로 판명된 토큰은 모아서 워커의 별도 짧은 트랜잭션으로 비활성화한다.
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
            worker.deactivateInvalidTokens(invalidTokenIds);
        } catch (final Exception e) {
            log.warn("Failed to deactivate invalid device tokens {}", invalidTokenIds, e);
        }
    }
}
