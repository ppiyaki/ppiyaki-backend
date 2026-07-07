package com.ppiyaki.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.ppiyaki.outbox.repository.OutboxMessageRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code OutboxMessageRepository.findClaimable}의 WHERE/ORDER BY/LIMIT 검증.
 * 단일 인스턴스 기준 단순 SELECT 폴링이므로 필터(PENDING + event_type + next_attempt_at<=now),
 * 정렬(created_at ASC), LIMIT만 검증하면 된다.
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
@DisplayName("OutboxMessageRepository.findClaimable 클레임 대상 선별")
class OutboxFindClaimableTest {

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        // 공유 H2(mem, ddl-auto=update)라 다른 테스트가 남긴 row가 결과에 섞이지 않게 비운다.
        outboxMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("PENDING이면서 event_type 일치, next_attempt_at<=now인 메시지만 클레임된다 (미래 예약·PROCESSED·FAILED·타 event_type 제외)")
    void findClaimable_filtersByStatusEventTypeAndNextAttemptAt() {
        // given: 상태/시각/이벤트 타입이 다른 5종의 메시지
        final LocalDateTime now = LocalDateTime.now(clock);
        final LocalDateTime past = now.minusHours(1);

        // 1) PENDING + next_attempt_at 과거 + 요청 event_type → 클레임 대상
        final Long claimableId = save(OutboxMessage.create("MEDICATION_COMPLETE", "claimable", past));
        // 2) PENDING + next_attempt_at 미래(백오프로 예약됨) → 제외
        final Long futureId = save(OutboxMessage.create("MEDICATION_COMPLETE", "future", now.plusHours(1)));
        // 3) PROCESSED (처리 완료) → next_attempt_at이 과거여도 제외
        final OutboxMessage processed = OutboxMessage.create("MEDICATION_COMPLETE", "processed", past);
        processed.markProcessed(past);
        final Long processedId = save(processed);
        // 4) FAILED (데드레터: 5회 실패) → next_attempt_at이 과거여도 제외
        final OutboxMessage failed = OutboxMessage.create("MEDICATION_COMPLETE", "failed", past);
        for (int i = 0; i < 5; i++) {
            failed.recordFailure("boom", past);
        }
        final Long failedId = save(failed);
        // 5) PENDING + 과거지만 다른 event_type → 제외 (다른 타입 relay의 메시지를 뺏지 않는다)
        final Long otherTypeId = save(OutboxMessage.create("OTHER_EVENT", "other-type", past));

        // when
        final List<Long> claimedIds = findClaimableIds(10);

        // then: PENDING + event_type 일치 + next_attempt_at<=now인 1건만
        assertThat(claimedIds).containsExactly(claimableId);
        assertThat(claimedIds).doesNotContain(futureId, processedId, failedId, otherTypeId);
    }

    @Test
    @DisplayName("클레임 대상은 created_at 오름차순으로 정렬되고 limit 건수만 반환된다")
    void findClaimable_ordersByCreatedAtAndRespectsLimit() {
        // given: 클레임 가능한 PENDING 3건. created_at을 저장 순서와 다르게 직접 세팅해
        // (JPA Auditing이 채우는 값을 덮어씀) 정렬 기준이 insert/id 순서가 아닌 created_at임을 증명한다.
        final LocalDateTime now = LocalDateTime.now(clock);
        final LocalDateTime past = now.minusHours(1);
        final Long firstSavedId = save(OutboxMessage.create("MEDICATION_COMPLETE", "first-saved", past));
        final Long secondSavedId = save(OutboxMessage.create("MEDICATION_COMPLETE", "second-saved", past));
        final Long thirdSavedId = save(OutboxMessage.create("MEDICATION_COMPLETE", "third-saved", past));
        overrideCreatedAt(firstSavedId, past.plusMinutes(3)); // 가장 늦게 생성된 것으로 세팅
        overrideCreatedAt(secondSavedId, past.plusMinutes(1)); // 가장 먼저 생성된 것으로 세팅
        overrideCreatedAt(thirdSavedId, past.plusMinutes(2));

        // when & then: created_at 오름차순: second → third → first
        assertThat(findClaimableIds(10))
                .containsExactly(secondSavedId, thirdSavedId, firstSavedId);

        // limit=2면 created_at이 빠른 2건만 반환된다
        assertThat(findClaimableIds(2))
                .containsExactly(secondSavedId, thirdSavedId);
    }

    // --- helpers ---

    private Long save(final OutboxMessage message) {
        return transactionTemplate.execute(status -> outboxMessageRepository.save(message).getId());
    }

    private List<Long> findClaimableIds(final int limit) {
        // 단순 SELECT라 트랜잭션 없이 호출한다.
        return outboxMessageRepository
                .findClaimable(OutboxService.MEDICATION_COMPLETE, LocalDateTime.now(clock), limit).stream()
                .map(OutboxMessage::getId)
                .toList();
    }

    /**
     * created_at은 JPA Auditing(@CreatedDate, updatable=false)이 채우므로 엔티티로는 조작할 수 없어
     * 정렬 검증을 위해 JdbcTemplate으로 직접 덮어쓴다.
     */
    private void overrideCreatedAt(final Long messageId, final LocalDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE outbox_message SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt), messageId);
    }
}
