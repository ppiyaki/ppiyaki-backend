package com.ppiyaki.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.outbox.repository.OutboxMessageRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
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
@DisplayName("OutboxService.enqueue 원자성 (도메인 트랜잭션과 함께 커밋/롤백)")
class OutboxEnqueueAtomicityTest {

    @Autowired
    private OutboxService outboxService;
    @Autowired
    private OutboxMessageRepository outboxMessageRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {

        outboxMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("도메인 트랜잭션이 롤백되면 enqueue된 outbox row도 함께 사라진다 (원자성)")
    void enqueue_rolledBackWithCallerTransaction() {

        final MedicationTakenEvent event = new MedicationTakenEvent(999901L, LocalDate.now());

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            outboxService.enqueue(OutboxService.MEDICATION_COMPLETE, event);
            throw new IllegalStateException("도메인 로직 실패를 가장한 의도적 롤백");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxMessageRepository.count()).isZero();
    }

    @Test
    @DisplayName("대조군: 도메인 트랜잭션이 정상 커밋되면 enqueue된 outbox row가 PENDING으로 남는다")
    void enqueue_committedWithCallerTransaction() {

        final MedicationTakenEvent event = new MedicationTakenEvent(999902L, LocalDate.now());

        transactionTemplate.executeWithoutResult(status ->
                outboxService.enqueue(OutboxService.MEDICATION_COMPLETE, event));

        assertThat(outboxMessageRepository.count()).isEqualTo(1);
        final OutboxMessage saved = outboxMessageRepository.findAll().get(0);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getEventType()).isEqualTo(OutboxService.MEDICATION_COMPLETE);
        assertThat(saved.getPayload()).contains("999902");
    }

    @Test
    @DisplayName("트랜잭션 밖에서 호출하면 예외를 던져 계약을 강제한다 (MANDATORY)")
    void enqueue_throwsWhenCalledWithoutTransaction() {

        final MedicationTakenEvent event = new MedicationTakenEvent(999903L, LocalDate.now());

        assertThatThrownBy(() ->
                outboxService.enqueue(OutboxService.MEDICATION_COMPLETE, event))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(outboxMessageRepository.count()).isZero();
    }
}
