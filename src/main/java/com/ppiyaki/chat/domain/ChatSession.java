package com.ppiyaki.chat.domain;

import com.ppiyaki.common.entity.BaseTimeEntity;
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

    public static ChatSession create() {
        return new ChatSession();
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
