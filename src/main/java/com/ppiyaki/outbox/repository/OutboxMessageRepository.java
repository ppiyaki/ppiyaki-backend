package com.ppiyaki.outbox.repository;

import com.ppiyaki.outbox.OutboxMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * relay가 처리할 PENDING 메시지를 클레임한다. FOR UPDATE SKIP LOCKED(MySQL 8)로
     * 멀티 인스턴스가 같은 row를 중복 클레임하지 않는다. 반드시 트랜잭션 안에서 호출할 것.
     *
     * <p>relay는 event_type별로 존재하므로 자기 타입의 메시지만 클레임해야 한다.
     * 필터가 없으면 다른 타입의 relay가 추가됐을 때 서로 메시지를 뺏어
     * "Unsupported eventType" 실패로 attempts만 소모시키게 된다.
     */
    @Query(value = """
            SELECT *
            FROM outbox_message
            WHERE status = 'PENDING'
              AND event_type = :eventType
              AND next_attempt_at <= :now
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxMessage> findClaimable(
            @Param("eventType") final String eventType,
            @Param("now") final LocalDateTime now,
            @Param("limit") final int limit);
}
