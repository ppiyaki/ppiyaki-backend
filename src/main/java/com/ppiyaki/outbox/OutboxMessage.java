package com.ppiyaki.outbox;

import com.ppiyaki.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Transactional Outbox 메시지. 도메인 트랜잭션과 같은 트랜잭션에서 INSERT되고({@link OutboxService#enqueue}),
 * 별도 relay({@link MedicationCompleteOutboxRelay})가 PENDING row를 클레임해 발행한다.
 */
@Getter
@Entity
@Table(
        name = "outbox_message",
        indexes = @Index(name = "idx_outbox_message_status_next_attempt", columnList = "status, next_attempt_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage extends BaseTimeEntity {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    /**
     * 지수 백오프 상한(초). maxAttempts가 커져도 재시도 간격이 5분을 넘지 않게 한다.
     */
    private static final long BACKOFF_CAP_SECONDS = 300L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    /**
     * 직렬화된 이벤트 JSON을 담는 불투명 blob. relay가 통째로 읽어 역직렬화만 하고
     * DB에서 JSON 경로 쿼리를 하지 않으므로 네이티브 JSON 타입이 아닌 TEXT로 저장한다.
     * (네이티브 JSON 타입은 H2/MySQL의 바인딩 semantics가 달라 이식성 문제를 유발한다.)
     */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    OutboxMessage(final String eventType, final String payload, final LocalDateTime nextAttemptAt) {
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null");
    }

    /**
     * status=PENDING, attempts=0, nextAttemptAt=now로 생성.
     * 테스트 용이성을 위해 now를 파라미터로 받는다(도메인 내부 LocalDateTime.now() 금지).
     */
    public static OutboxMessage create(final String eventType, final String payload, final LocalDateTime now) {
        return new OutboxMessage(eventType, payload, now);
    }

    public void markProcessed(final LocalDateTime now) {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = Objects.requireNonNull(now, "now must not be null");
        this.lastError = null;
    }

    /**
     * 발행 실패 기록. attempts를 증가시키고 maxAttempts에 도달하면 FAILED(데드레터),
     * 아니면 PENDING을 유지하며 nextAttemptAt을 지수 백오프(now + min(2^attempts, 300)초)로 미룬다.
     */
    public void recordFailure(final String error, final LocalDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        this.attempts++;
        this.lastError = error;
        if (this.attempts >= this.maxAttempts) {
            this.status = OutboxStatus.FAILED;
            return;
        }
        this.nextAttemptAt = now.plusSeconds(backoffSeconds(this.attempts));
    }

    private static long backoffSeconds(final int attempts) {
        // attempts가 커져도 시프트 오버플로 없이 상한에 수렴하도록 방어한다.
        if (attempts >= Long.SIZE - 1) {
            return BACKOFF_CAP_SECONDS;
        }
        return Math.min(1L << attempts, BACKOFF_CAP_SECONDS);
    }
}
