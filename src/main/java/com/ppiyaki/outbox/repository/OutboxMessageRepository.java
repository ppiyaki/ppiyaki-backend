package com.ppiyaki.outbox.repository;

import com.ppiyaki.outbox.OutboxMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    @Query(value = """
            SELECT *
            FROM outbox_message
            WHERE status = 'PENDING'
              AND event_type = :eventType
              AND next_attempt_at <= :now
            ORDER BY created_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<OutboxMessage> findClaimable(
            @Param("eventType") final String eventType,
            @Param("now") final LocalDateTime now,
            @Param("limit") final int limit);
}
