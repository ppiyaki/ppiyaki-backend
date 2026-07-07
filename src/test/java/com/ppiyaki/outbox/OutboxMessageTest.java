package com.ppiyaki.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OutboxMessage 상태 전이 (재시도 백오프 · 데드레터 · 처리 완료)")
class OutboxMessageTest {

    // 도메인이 LocalDateTime.now()를 내부 호출하지 않고 now를 주입받으므로 고정 시각으로 결정적으로 검증한다.
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 7, 10, 0, 0);

    @Test
    @DisplayName("create()로 생성하면 status=PENDING, attempts=0, nextAttemptAt=now, maxAttempts=5이다")
    void create_initialState() {
        // given & when
        final OutboxMessage message = OutboxMessage.create("MEDICATION_COMPLETE", "{}", NOW);

        // then
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
        // given
        final OutboxMessage message = OutboxMessage.create("MEDICATION_COMPLETE", "{}", NOW);

        // when & then — 실패 1회차: attempts=1, nextAttemptAt=now+2s
        message.recordFailure("boom-1", NOW);
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(message.getLastError()).isEqualTo("boom-1");

        // 실패 2회차: attempts=2, nextAttemptAt=now+4s
        message.recordFailure("boom-2", NOW);
        assertThat(message.getAttempts()).isEqualTo(2);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(4));

        // 실패 3회차: attempts=3, nextAttemptAt=now+8s
        message.recordFailure("boom-3", NOW);
        assertThat(message.getAttempts()).isEqualTo(3);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(8));

        // 실패 4회차: attempts=4, nextAttemptAt=now+16s — 아직 PENDING (maxAttempts=5 미달)
        message.recordFailure("boom-4", NOW);
        assertThat(message.getAttempts()).isEqualTo(4);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(16));
    }

    @Test
    @DisplayName("attempts가 maxAttempts(5)에 도달하면 FAILED(데드레터)로 전이하고 nextAttemptAt은 더 이상 바뀌지 않는다")
    void recordFailure_reachesMaxAttempts_becomesFailedDeadLetter() {
        // given — 4회 실패까지 진행 (nextAttemptAt=now+16s)
        final OutboxMessage message = OutboxMessage.create("MEDICATION_COMPLETE", "{}", NOW);
        for (int i = 1; i <= 4; i++) {
            message.recordFailure("boom-" + i, NOW);
        }
        final LocalDateTime nextAttemptBeforeDeadLetter = message.getNextAttemptAt();

        // when — 5회차 실패로 maxAttempts 도달
        message.recordFailure("boom-final", NOW);

        // then — FAILED 전이, nextAttemptAt은 4회차 값 그대로 (더 미루지 않음)
        assertThat(message.getAttempts()).isEqualTo(5);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(message.getNextAttemptAt()).isEqualTo(nextAttemptBeforeDeadLetter);
        assertThat(message.getLastError()).isEqualTo("boom-final");

        // FAILED 이후 추가 실패가 기록돼도 nextAttemptAt은 바뀌지 않고 FAILED가 유지된다
        message.recordFailure("boom-after-dead", NOW.plusMinutes(10));
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(message.getNextAttemptAt()).isEqualTo(nextAttemptBeforeDeadLetter);
    }

    @Test
    @DisplayName("markProcessed 시 status=PROCESSED, processedAt=주입한 now, lastError는 초기화된다")
    void markProcessed_setsProcessedStateAndClearsLastError() {
        // given — 한 번 실패해 lastError가 남아 있는 메시지
        final OutboxMessage message = OutboxMessage.create("MEDICATION_COMPLETE", "{}", NOW);
        message.recordFailure("transient-error", NOW);
        final LocalDateTime processedNow = NOW.plusSeconds(30);

        // when
        message.markProcessed(processedNow);

        // then
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(message.getProcessedAt()).isEqualTo(processedNow);
        assertThat(message.getLastError()).isNull();
    }
}
