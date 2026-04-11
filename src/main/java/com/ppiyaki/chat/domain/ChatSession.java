package com.ppiyaki.chat.domain;

import com.ppiyaki.common.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
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
        return getUpdatedAt().plusMinutes(expirationMinutes).isBefore(now);
    }
}
