package com.ppiyaki.notification;

import com.ppiyaki.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "device_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token", unique = true, nullable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    private DevicePlatform platform;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    DeviceToken(
            final Long userId,
            final String token,
            final DevicePlatform platform,
            final LocalDateTime lastSeenAt
    ) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.token = Objects.requireNonNull(token, "token must not be null");
        this.platform = Objects.requireNonNull(platform, "platform must not be null");
        this.isActive = true;
        this.lastSeenAt = lastSeenAt;
    }

    public static DeviceToken register(final Long userId, final String token, final DevicePlatform platform) {
        return new DeviceToken(userId, token, platform, LocalDateTime.now());
    }

    public void reactivate(final LocalDateTime lastSeenAt) {
        this.isActive = true;
        this.lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
    }

    /**
     * 같은 device가 다른 사용자 계정으로 로그인 후 재등록되는 케이스의 소유자 이전.
     * issue #329: token UNIQUE 제약 때문에 신규 INSERT 불가 → 같은 row의 owner를 갱신.
     * lastSeenAt 갱신 + isActive=true.
     */
    public void transferTo(final Long newUserId, final LocalDateTime lastSeenAt) {
        this.userId = Objects.requireNonNull(newUserId, "newUserId must not be null");
        this.isActive = true;
        this.lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
    }

    public void deactivate() {
        this.isActive = false;
    }
}
