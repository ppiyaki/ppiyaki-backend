package com.ppiyaki.user;

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
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    RefreshToken(
            final Long userId,
            final String token,
            final LocalDateTime expiresAt
    ) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.token = Objects.requireNonNull(token, "token must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void rotate(final String newToken, final LocalDateTime newExpiresAt) {
        this.token = Objects.requireNonNull(newToken, "newToken must not be null");
        this.expiresAt = Objects.requireNonNull(newExpiresAt, "newExpiresAt must not be null");
    }
}
