package com.ppiyaki.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OutboxMessage 상태 전이 (재시도 백오프 · 데드레터 · 처리 완료)")
class OutboxMessageTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 7, 10, 0, 0);

    @Test
    @DisplayName("create()로 생성하면 status=PENDING, attempts=0, nextAttemptAt=now, maxAttempts=5이다")
    void create_initialState() {

        final OutboxMessage message = OutboxMessage.create("MEDICATION_COMPLETE", "{}", NOW);

        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getAttempts()).isEqualTo(0);
        assertThat(message.getMaxAttempts()).isEqualTo(5);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(message.getProcessedAt()).isNull();
        assertThat(message.getLastError()).isNull();
    }

    @Test
    @DisplayName("recordFailure 반복 시 attempts가 증가하고 nextAttemptAt이 now+2s, +4s, +8s, +16s 지수 백오프로 미뤄진다")
    void recordFailure_exponentialBackoff() {

        final OutboxMessage message = OutboxMessage.create("MEDICATION_COMPLETE", "{}", NOW);

        message.recordFailure("boom-1", NOW);
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(message.getLastError()).isEqualTo("boom-1");

        message.recordFailure("boom-2", NOW);
        assertThat(message.getAttempts()).isEqualTo(2);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(4));

        message.recordFailure("boom-3", NOW);
        assertThat(message.getAttempts()).isEqualTo(3);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(8));

        message.recordFailure("boom-4", NOW);
        assertThat(message.getAttempts()).isEqualTo(4);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(16));
    }

    @Test
    @DisplayName("attempts가 maxAttempts(5)에 도달하면 FAILED(데드레터)로 전이하고 nextAttemptAt은 더 이상 바뀌지 않는다")
    void recordFailure_reachesMaxAttempts_becomesFailedDeadLetter() {

        final OutboxMessage message = OutboxMessage.create("MEDICATION_COMPLETE", "{}", NOW);
        for (int i = 1; i <= 4; i++) {
            message.recordFailure("boom-" + i, NOW);
        }
        final LocalDateTime nextAttemptBeforeDeadLetter = message.getNextAttemptAt();

        message.recordFailure("boom-final", NOW);

        assertThat(message.getAttempts()).isEqualTo(5);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(message.getNextAttemptAt()).isEqualTo(nextAttemptBeforeDeadLetter);
        assertThat(message.getLastError()).isEqualTo("boom-final");

        message.recordFailure("boom-after-dead", NOW.plusMinutes(10));
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(message.getNextAttemptAt()).isEqualTo(nextAttemptBeforeDeadLetter);
    }

    @Test
    @DisplayName("markProcessed 시 status=PROCESSED, processedAt=주입한 now, lastError는 초기화된다")
    void markProcessed_setsProcessedStateAndClearsLastError() {

        final OutboxMessage message = OutboxMessage.create("MEDICATION_COMPLETE", "{}", NOW);
        message.recordFailure("transient-error", NOW);
        final LocalDateTime processedNow = NOW.plusSeconds(30);

        message.markProcessed(processedNow);

        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(message.getProcessedAt()).isEqualTo(processedNow);
        assertThat(message.getLastError()).isNull();
    }
}
