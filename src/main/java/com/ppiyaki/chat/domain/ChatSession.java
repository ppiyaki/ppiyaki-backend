package com.ppiyaki.chat.domain;

import com.ppiyaki.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "chat_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public static ChatSession create(final Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        final ChatSession chatSession = new ChatSession();
        chatSession.userId = userId;
        return chatSession;
    }

    public boolean isOwnedBy(final Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return this.userId.equals(userId);
    }

    public void touch() {
        // @LastModifiedDate가 동작하도록 dirty 상태를 만든다
        this.userId = this.userId;
    }

    public boolean isExpired(final LocalDateTime now, final long expirationMinutes) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(getUpdatedAt(), "updatedAt must not be null");
        if (expirationMinutes < 0) {
            throw new IllegalArgumentException("expirationMinutes must be >= 0");
        }
        final LocalDateTime expiry = getUpdatedAt().plusMinutes(expirationMinutes);
        return !now.isBefore(expiry);
    }
}
