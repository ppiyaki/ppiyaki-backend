package com.ppiyaki.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppiyaki.outbox.repository.OutboxMessageRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * Transactional Outbox 적재 서비스.
 *
 * <p><b>반드시 도메인 트랜잭션 내에서 호출할 것.</b> 이 서비스는 의도적으로 자체 트랜잭션을 열지 않는다
 * (no {@code @Transactional}) — outbox INSERT가 호출자의 트랜잭션에 합류해야
 * "도메인 상태 변경 + outbox 적재"가 원자적으로 커밋/롤백되는 것이 Outbox 패턴의 핵심이기 때문이다.
 * 적재된 메시지는 별도 relay(예: {@link MedicationCompleteOutboxRelay})가 폴링해 발행한다.
 */
@Service
public class OutboxService {

    /** 복약 완료(첫 TAKEN 확정) 이벤트 타입. payload = {@code MedicationTakenEvent} JSON. */
    public static final String MEDICATION_COMPLETE = "MEDICATION_COMPLETE";

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(
            final OutboxMessageRepository outboxMessageRepository,
            final ObjectMapper objectMapper,
            final Clock clock
    ) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * payload를 JSON으로 직렬화해 PENDING outbox 메시지로 저장한다.
     * 호출자의 트랜잭션에 합류하므로 반드시 도메인 트랜잭션 내에서 호출해야 한다.
     */
    public OutboxMessage enqueue(final String eventType, final Object payload) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (final JsonProcessingException e) {
            // 직렬화 실패는 프로그래밍 오류(직렬화 불가능한 payload 타입) — 도메인 트랜잭션과 함께 실패시킨다.
            throw new IllegalArgumentException(
                    "Failed to serialize outbox payload for eventType=" + eventType, e);
        }
        return outboxMessageRepository.save(OutboxMessage.create(eventType, json, LocalDateTime.now(clock)));
    }
}
