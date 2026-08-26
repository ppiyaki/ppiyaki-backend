package com.ppiyaki.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "openai.api-key=sk-test-placeholder",
        "openai.model=gpt-test",
        "ncp.storage.endpoint=https://kr.object.ncloudstorage.com",
        "ncp.storage.region=kr-standard",
        "ncp.storage.access-key=test-access-key",
        "ncp.storage.secret-key=test-secret-key",
        "ncp.storage.bucket-name=ppiyaki-test",

        "outbox.relay.initial-delay-ms=3600000"
})
@DisplayName("복약 완료 Outbox relay 재시도 → 데드레터 전이 (실제 dispatcher 트랜잭션 경계에서 실패)")
class MedicationCompleteOutboxRelayRetryTest {

    private static final long POISON_SENIOR_ID = 999911L;

    private static final long HEALTHY_SENIOR_ID = 999912L;

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
    private MedicationScheduleRepository scheduleRepository;

    @BeforeEach
    void setUp() {

        outboxMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("dispatch 실패가 반복되면 attempts 증가·PENDING 유지·nextAttemptAt 연기, maxAttempts(5) 도달 시 FAILED로 전이 후 재처리되지 않는다")
    void repeatedDispatchFailure_retriesThenDeadLetters() {

        when(scheduleRepository.findActiveByOwnerAndDate(eq(POISON_SENIOR_ID), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("dispatch 실패 시뮬레이션"));
        final Long messageId = enqueue(POISON_SENIOR_ID);

        for (int attempt = 1; attempt <= 4; attempt++) {
            relay.poll();
            final OutboxMessage message = outboxMessageRepository.findById(messageId).orElseThrow();
            assertThat(message.getAttempts()).isEqualTo(attempt);
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(message.getLastError()).contains("dispatch 실패 시뮬레이션");

            assertThat(message.getNextAttemptAt()).isAfter(LocalDateTime.now(clock));

            if (attempt == 1) {

                relay.poll();
                assertThat(outboxMessageRepository.findById(messageId).orElseThrow().getAttempts())
                        .isEqualTo(1);
            }

            rewindNextAttemptAt(messageId);
        }

        relay.poll();
        final OutboxMessage deadLettered = outboxMessageRepository.findById(messageId).orElseThrow();
        assertThat(deadLettered.getAttempts()).isEqualTo(5);
        assertThat(deadLettered.getStatus()).isEqualTo(OutboxStatus.FAILED);

        rewindNextAttemptAt(messageId);
        final List<Long> claimableIds = transactionTemplate.execute(status -> outboxMessageRepository
                .findClaimable(OutboxService.MEDICATION_COMPLETE, LocalDateTime.now(clock), 10).stream()
                .map(OutboxMessage::getId)
                .toList());
        assertThat(claimableIds).doesNotContain(messageId);

        relay.poll();
        final OutboxMessage afterExtraPoll = outboxMessageRepository.findById(messageId).orElseThrow();
        assertThat(afterExtraPoll.getAttempts()).isEqualTo(5);
        assertThat(afterExtraPoll.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(scheduleRepository, times(5))
                .findActiveByOwnerAndDate(eq(POISON_SENIOR_ID), any(LocalDate.class));
    }

    @Test
    @DisplayName("poison 메시지 1건이 있어도 같은 배치의 정상 메시지는 PROCESSED 되고 poison만 attempts가 증가한다 (메시지 단위 격리)")
    void poisonMessage_doesNotAffectHealthyMessagesInSameBatch() {

        when(scheduleRepository.findActiveByOwnerAndDate(eq(POISON_SENIOR_ID), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("poison dispatch 실패 시뮬레이션"));
        when(scheduleRepository.findActiveByOwnerAndDate(eq(HEALTHY_SENIOR_ID), any(LocalDate.class)))
                .thenReturn(List.of());
        final Long poisonId = enqueue(POISON_SENIOR_ID);
        final Long healthyId = enqueue(HEALTHY_SENIOR_ID);
        final LocalDateTime now = LocalDateTime.now(clock);
        overrideCreatedAt(poisonId, now.minusMinutes(2));
        overrideCreatedAt(healthyId, now.minusMinutes(1));

        relay.poll();

        final OutboxMessage healthy = outboxMessageRepository.findById(healthyId).orElseThrow();
        assertThat(healthy.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(healthy.getAttempts()).isEqualTo(0);
        assertThat(healthy.getProcessedAt()).isNotNull();

        final OutboxMessage poison = outboxMessageRepository.findById(poisonId).orElseThrow();
        assertThat(poison.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(poison.getAttempts()).isEqualTo(1);
        assertThat(poison.getLastError()).contains("poison dispatch 실패 시뮬레이션");
        assertThat(poison.getNextAttemptAt()).isAfter(LocalDateTime.now(clock));
    }

    private Long enqueue(final long seniorId) {
        return transactionTemplate.execute(status -> outboxService
                .enqueue(OutboxService.MEDICATION_COMPLETE, new MedicationTakenEvent(seniorId, LocalDate.now()))
                .getId());
    }

    private void rewindNextAttemptAt(final Long messageId) {
        jdbcTemplate.update(
                "UPDATE outbox_message SET next_attempt_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now(clock).minusMinutes(1)), messageId);
    }

    private void overrideCreatedAt(final Long messageId, final LocalDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE outbox_message SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt), messageId);
    }
}
