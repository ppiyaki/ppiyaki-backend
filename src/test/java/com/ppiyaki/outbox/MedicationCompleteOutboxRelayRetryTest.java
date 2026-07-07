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

/**
 * Outbox relay <b>재시도 → 데드레터</b> 검증: dispatch가 계속 실패하면 attempts가 증가하며
 * PENDING을 유지하다가 maxAttempts(5) 도달 시 FAILED(데드레터)로 전이하고, 이후 재처리되지 않는다.
 *
 * <p><b>실패 주입 방식</b>: dispatcher 자체를 mock으로 갈아끼우지 않는다. 그러면
 * {@code @Transactional} 프록시가 제거되어 rollback-only 마킹이 일어나지 않는, 프로덕션에 없는
 * 조건에서만 통과하는 테스트가 되기 때문이다. 대신 dispatcher 내부 의존성인
 * {@link MedicationScheduleRepository}만 mock으로 바꿔, <b>실제 dispatcher 빈의 실제
 * {@code @Transactional(REQUIRES_NEW)} 경계 안</b>에서 예외가 터지게 한다. 따라서 이 테스트는
 * dispatch가 REQUIRED로 relay 트랜잭션에 합류하면(rollback-only 전파로 recordFailure가 롤백되어)
 * 실패하고, REQUIRES_NEW 격리가 있어야만 통과한다.
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
@DisplayName("복약 완료 Outbox relay 재시도 → 데드레터 전이 (실제 dispatcher 트랜잭션 경계에서 실패)")
class MedicationCompleteOutboxRelayRetryTest {

    /** dispatch 실패를 주입할 poison 메시지의 seniorId. 이 값에 대해서만 mock repo가 예외를 던진다. */
    private static final long POISON_SENIOR_ID = 999911L;
    /** 같은 배치의 정상 메시지 seniorId. mock repo가 빈 목록을 반환해 dispatch가 정상 종료된다. */
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

    /**
     * dispatcher가 아니라 그 내부 의존성을 mock한다. 실제 dispatcher 빈과 그 트랜잭션 프록시는
     * 그대로 살아 있으므로, 예외가 진짜 {@code REQUIRES_NEW} 트랜잭션 경계 안에서 발생한다.
     * (stub하지 않은 호출은 Mockito 기본값인 빈 목록을 반환해 다른 스케줄러에 영향이 없다.)
     */
    @MockBean
    private MedicationScheduleRepository scheduleRepository;

    @BeforeEach
    void setUp() {
        // 공유 H2(mem, ddl-auto=update)라 다른 테스트가 남긴 PENDING row가 배치에 끼어들지 않게 비운다.
        outboxMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("dispatch 실패가 반복되면 attempts 증가·PENDING 유지·nextAttemptAt 연기, maxAttempts(5) 도달 시 FAILED로 전이 후 재처리되지 않는다")
    void repeatedDispatchFailure_retriesThenDeadLetters() {
        // given - 실제 dispatcher의 REQUIRES_NEW 트랜잭션 안(첫 repo 호출)에서 항상 예외 + PENDING 메시지 1건 적재
        when(scheduleRepository.findActiveByOwnerAndDate(eq(POISON_SENIOR_ID), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("dispatch 실패 시뮬레이션"));
        final Long messageId = enqueue(POISON_SENIOR_ID);

        // when & then - 1~4회차 실패: 매번 attempts 증가 + PENDING 유지 + nextAttemptAt이 미래로 연기된다
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
                .findClaimable(OutboxService.MEDICATION_COMPLETE, LocalDateTime.now(clock), 10).stream()
                .map(OutboxMessage::getId)
                .toList());
        assertThat(claimableIds).doesNotContain(messageId);

        // 이후 poll에서도 재처리되지 않는다 - dispatch 총 호출 횟수는 실패한 5회 그대로
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
        // given - poison(예외)과 healthy(빈 schedule 목록 → 정상 종료) 메시지를 한 배치에 적재.
        // poison의 created_at을 더 과거로 세팅해 배치 안에서 poison이 먼저 처리되게 한다
        // (앞선 실패가 뒤의 정상 처리를 오염시키지 않음을 증명).
        when(scheduleRepository.findActiveByOwnerAndDate(eq(POISON_SENIOR_ID), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("poison dispatch 실패 시뮬레이션"));
        when(scheduleRepository.findActiveByOwnerAndDate(eq(HEALTHY_SENIOR_ID), any(LocalDate.class)))
                .thenReturn(List.of());
        final Long poisonId = enqueue(POISON_SENIOR_ID);
        final Long healthyId = enqueue(HEALTHY_SENIOR_ID);
        final LocalDateTime now = LocalDateTime.now(clock);
        overrideCreatedAt(poisonId, now.minusMinutes(2));
        overrideCreatedAt(healthyId, now.minusMinutes(1));

        // when - 두 메시지가 같은 배치로 클레임된다 (batch-size 기본 50)
        relay.poll();

        // then - healthy는 poison 실패와 무관하게 PROCESSED로 커밋된다
        final OutboxMessage healthy = outboxMessageRepository.findById(healthyId).orElseThrow();
        assertThat(healthy.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(healthy.getAttempts()).isEqualTo(0);
        assertThat(healthy.getProcessedAt()).isNotNull();

        // poison만 실패가 기록된다 (attempts 증가 + PENDING 유지 + 백오프)
        final OutboxMessage poison = outboxMessageRepository.findById(poisonId).orElseThrow();
        assertThat(poison.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(poison.getAttempts()).isEqualTo(1);
        assertThat(poison.getLastError()).contains("poison dispatch 실패 시뮬레이션");
        assertThat(poison.getNextAttemptAt()).isAfter(LocalDateTime.now(clock));
    }

    // --- helpers ---

    private Long enqueue(final long seniorId) {
        return transactionTemplate.execute(status -> outboxService
                .enqueue(OutboxService.MEDICATION_COMPLETE, new MedicationTakenEvent(seniorId, LocalDate.now()))
                .getId());
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

    /**
     * created_at은 JPA Auditing(@CreatedDate, updatable=false)이 채우므로 엔티티로는 조작할 수 없어
     * 배치 내 처리 순서(created_at ASC)를 고정하기 위해 JdbcTemplate으로 직접 덮어쓴다.
     */
    private void overrideCreatedAt(final Long messageId, final LocalDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE outbox_message SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt), messageId);
    }
}
