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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox 패턴의 핵심 보증인 <b>enqueue 원자성</b> 검증:
 * enqueue는 자체 트랜잭션을 열지 않고 호출자(도메인) 트랜잭션에 합류하므로,
 * 도메인 트랜잭션이 롤백되면 outbox row도 함께 사라지고 커밋되면 함께 남아야 한다.
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
        // 공유 H2(mem, ddl-auto=update)라 다른 테스트가 남긴 row가 있을 수 있어 outbox를 비우고 시작한다.
        outboxMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("도메인 트랜잭션이 롤백되면 enqueue된 outbox row도 함께 사라진다 (원자성)")
    void enqueue_rolledBackWithCallerTransaction() {
        // given: 프로그래매틱 트랜잭션 안에서 enqueue 후 의도적으로 예외를 던져 롤백시킨다
        final MedicationTakenEvent event = new MedicationTakenEvent(999901L, LocalDate.now());

        // when: enqueue는 호출자 트랜잭션에 합류하므로 예외로 인한 롤백에 outbox INSERT도 휩쓸려야 한다
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            outboxService.enqueue(OutboxService.MEDICATION_COMPLETE, event);
            throw new IllegalStateException("도메인 로직 실패를 가장한 의도적 롤백");
        })).isInstanceOf(IllegalStateException.class);

        // then: 롤백 후 별도 트랜잭션에서 조회하면 outbox row가 남아 있지 않아야 한다
        assertThat(outboxMessageRepository.count()).isZero();
    }

    @Test
    @DisplayName("대조군: 도메인 트랜잭션이 정상 커밋되면 enqueue된 outbox row가 PENDING으로 남는다")
    void enqueue_committedWithCallerTransaction() {
        // given
        final MedicationTakenEvent event = new MedicationTakenEvent(999902L, LocalDate.now());

        // when: 예외 없이 정상 커밋
        transactionTemplate.executeWithoutResult(status ->
                outboxService.enqueue(OutboxService.MEDICATION_COMPLETE, event));

        // then: 커밋 후 row 1건이 PENDING으로 남고 payload에 이벤트가 직렬화되어 있다
        assertThat(outboxMessageRepository.count()).isEqualTo(1);
        final OutboxMessage saved = outboxMessageRepository.findAll().get(0);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getEventType()).isEqualTo(OutboxService.MEDICATION_COMPLETE);
        assertThat(saved.getPayload()).contains("999902");
    }
}
