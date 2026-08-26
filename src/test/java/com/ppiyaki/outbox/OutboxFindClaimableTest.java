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

        outboxMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("PENDING이면서 event_type 일치, next_attempt_at<=now인 메시지만 클레임된다 (미래 예약·PROCESSED·FAILED·타 event_type 제외)")
    void findClaimable_filtersByStatusEventTypeAndNextAttemptAt() {

        final LocalDateTime now = LocalDateTime.now(clock);
        final LocalDateTime past = now.minusHours(1);

        final Long claimableId = save(OutboxMessage.create("MEDICATION_COMPLETE", "claimable", past));

        final Long futureId = save(OutboxMessage.create("MEDICATION_COMPLETE", "future", now.plusHours(1)));

        final OutboxMessage processed = OutboxMessage.create("MEDICATION_COMPLETE", "processed", past);
        processed.markProcessed(past);
        final Long processedId = save(processed);

        final OutboxMessage failed = OutboxMessage.create("MEDICATION_COMPLETE", "failed", past);
        for (int i = 0; i < 5; i++) {
            failed.recordFailure("boom", past);
        }
        final Long failedId = save(failed);

        final Long otherTypeId = save(OutboxMessage.create("OTHER_EVENT", "other-type", past));

        final List<Long> claimedIds = findClaimableIds(10);

        assertThat(claimedIds).containsExactly(claimableId);
        assertThat(claimedIds).doesNotContain(futureId, processedId, failedId, otherTypeId);
    }

    @Test
    @DisplayName("클레임 대상은 created_at 오름차순으로 정렬되고 limit 건수만 반환된다")
    void findClaimable_ordersByCreatedAtAndRespectsLimit() {

        final LocalDateTime now = LocalDateTime.now(clock);
        final LocalDateTime past = now.minusHours(1);
        final Long firstSavedId = save(OutboxMessage.create("MEDICATION_COMPLETE", "first-saved", past));
        final Long secondSavedId = save(OutboxMessage.create("MEDICATION_COMPLETE", "second-saved", past));
        final Long thirdSavedId = save(OutboxMessage.create("MEDICATION_COMPLETE", "third-saved", past));
        overrideCreatedAt(firstSavedId, past.plusMinutes(3));
        overrideCreatedAt(secondSavedId, past.plusMinutes(1));
        overrideCreatedAt(thirdSavedId, past.plusMinutes(2));

        assertThat(findClaimableIds(10))
                .containsExactly(secondSavedId, thirdSavedId, firstSavedId);

        assertThat(findClaimableIds(2))
                .containsExactly(secondSavedId, thirdSavedId);
    }

    private Long save(final OutboxMessage message) {
        return transactionTemplate.execute(status -> outboxMessageRepository.save(message).getId());
    }

    private List<Long> findClaimableIds(final int limit) {

        return outboxMessageRepository
                .findClaimable(OutboxService.MEDICATION_COMPLETE, LocalDateTime.now(clock), limit).stream()
                .map(OutboxMessage::getId)
                .toList();
    }

    private void overrideCreatedAt(final Long messageId, final LocalDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE outbox_message SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt), messageId);
    }
}
