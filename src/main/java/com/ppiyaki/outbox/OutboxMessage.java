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

@Getter
@Entity
@Table(
        name = "outbox_message",
        indexes = @Index(name = "idx_outbox_message_status_next_attempt", columnList = "status, next_attempt_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage extends BaseTimeEntity {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

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

    public static OutboxMessage create(final String eventType, final String payload, final LocalDateTime now) {
        return new OutboxMessage(eventType, payload, now);
    }

    public void markProcessed(final LocalDateTime now) {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = Objects.requireNonNull(now, "now must not be null");
        this.lastError = null;
    }

    public void recordFailure(final String error, final LocalDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        this.attempts++;
        this.lastError = error;
        if (this.attempts >= this.maxAttempts) {
            this.status = OutboxStatus.FAILED;
            return;
        }
        this.nextAttemptAt = now.plusSeconds(1L << this.attempts);
    }
}
