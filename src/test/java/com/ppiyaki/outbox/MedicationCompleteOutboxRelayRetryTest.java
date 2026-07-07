package com.ppiyaki.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.notification.service.MedicationCompleteDispatcher;
import com.ppiyaki.outbox.repository.OutboxMessageRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox relay <b>재시도 → 데드레터</b> 검증: dispatch가 계속 실패하면 attempts가 증가하며
 * PENDING을 유지하다가 maxAttempts(5) 도달 시 FAILED(데드레터)로 전이하고, 이후 재처리되지 않는다.
 *
 * <p>recordFailure의 지수 백오프로 nextAttemptAt이 미래로 밀리면 다음 poll이 클레임하지 못하므로,
 * 테스트에서는 각 재시도 사이에 next_attempt_at을 과거로 직접 되감아(JdbcTemplate) 결정적으로 진행한다
 * (실제 시간 대기·sleep 없음).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "openai.api-key=sk-test-placeholder",
        "openai.model=gpt-test",
        "ncp.storage.endpoint=https://kr.object.ncloudstorage.com",
        "ncp.storage.region=kr-standard",
        "ncp.storage.access-key=test-access-key",
        "ncp.storage.secret-key=test-secret-key",
        "ncp.storage.bucket-name=ppiyaki-test",
        // 백그라운드 @Scheduled 릴레이가 테스트 도중 끼어들지 않게 initial delay를 크게 잡는다.
        "outbox.relay.initial-delay-ms=3600000"
})
@DisplayName("복약 완료 Outbox relay 재시도 → 데드레터 전이")
class MedicationCompleteOutboxRelayRetryTest {

    @Autowired
    private MedicationCompleteOutboxRelay relay;
    @Autowired
    private OutboxService outboxService;
    @Autowired
    private OutboxMessageRepository outboxMessageRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;

    @MockBean
    private MedicationCompleteDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        // 공유 H2(mem, ddl-auto=update)라 다른 테스트가 남긴 PENDING row가 mock 호출 횟수를 오염시키지 않게 비운다.
        outboxMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("dispatch 실패가 반복되면 attempts 증가·PENDING 유지·nextAttemptAt 연기, maxAttempts(5) 도달 시 FAILED로 전이 후 재처리되지 않는다")
    void repeatedDispatchFailure_retriesThenDeadLetters() {
        // given — dispatcher가 항상 예외를 던지도록 스텁 + PENDING 메시지 1건 적재
        when(dispatcher.dispatchCompletedSlots(anyLong(), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("dispatch 실패 시뮬레이션"));
        final Long messageId = transactionTemplate.execute(status -> outboxService
                .enqueue(OutboxService.MEDICATION_COMPLETE, new MedicationTakenEvent(999911L, LocalDate.now()))
                .getId());

        // when & then — 1~4회차 실패: 매번 attempts 증가 + PENDING 유지 + nextAttemptAt이 미래로 연기된다
        for (int attempt = 1; attempt <= 4; attempt++) {
            relay.poll();
            final OutboxMessage message = outboxMessageRepository.findById(messageId).orElseThrow();
            assertThat(message.getAttempts()).isEqualTo(attempt);
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(message.getLastError()).contains("dispatch 실패 시뮬레이션");
            // 지수 백오프로 nextAttemptAt이 현재(릴레이와 같은 Clock 기준)보다 미래로 밀려 있다
            assertThat(message.getNextAttemptAt()).isAfter(LocalDateTime.now(clock));

            if (attempt == 1) {
                // 백오프가 걸린 상태에서 곧바로 poll해도 클레임되지 않는다 (즉시 재처리 없음)
                relay.poll();
                assertThat(outboxMessageRepository.findById(messageId).orElseThrow().getAttempts())
                        .isEqualTo(1);
            }
            // 다음 재시도를 결정적으로 진행하기 위해 next_attempt_at을 과거로 되감는다
            rewindNextAttemptAt(messageId);
        }

        // 5회차 실패 → maxAttempts 도달 → FAILED(데드레터) 전이
        relay.poll();
        final OutboxMessage deadLettered = outboxMessageRepository.findById(messageId).orElseThrow();
        assertThat(deadLettered.getAttempts()).isEqualTo(5);
        assertThat(deadLettered.getStatus()).isEqualTo(OutboxStatus.FAILED);

        // FAILED는 next_attempt_at이 과거여도 findClaimable에 잡히지 않는다 (status 필터)
        rewindNextAttemptAt(messageId);
        final List<Long> claimableIds = transactionTemplate.execute(status -> outboxMessageRepository
                .findClaimable(LocalDateTime.now(clock), 10).stream()
                .map(OutboxMessage::getId)
                .toList());
        assertThat(claimableIds).doesNotContain(messageId);

        // 이후 poll에서도 재처리되지 않는다 — dispatch 총 호출 횟수는 실패한 5회 그대로
        relay.poll();
        final OutboxMessage afterExtraPoll = outboxMessageRepository.findById(messageId).orElseThrow();
        assertThat(afterExtraPoll.getAttempts()).isEqualTo(5);
        assertThat(afterExtraPoll.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(dispatcher, times(5)).dispatchCompletedSlots(anyLong(), any(LocalDate.class));
    }

    /**
     * 백오프로 미래로 밀린 next_attempt_at을 과거로 되감아 다음 poll이 즉시 클레임할 수 있게 한다.
     * (도메인에 테스트용 setter를 추가하지 않기 위한 테스트 전용 시간 조작.)
     */
    private void rewindNextAttemptAt(final Long messageId) {
        jdbcTemplate.update(
                "UPDATE outbox_message SET next_attempt_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now(clock).minusMinutes(1)), messageId);
    }
}
