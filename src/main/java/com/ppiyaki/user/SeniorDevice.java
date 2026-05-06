package com.ppiyaki.user;

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

@Getter
@Entity
@Table(name = "senior_devices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeniorDevice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_id", nullable = false)
    private Long seniorId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "refresh_token_hash")
    private String refreshTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SeniorDeviceStatus status;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public SeniorDevice(
            final Long seniorId,
            final String deviceId,
            final String deviceName,
            final String refreshTokenHash
    ) {
        this.seniorId = Objects.requireNonNull(seniorId, "seniorId must not be null");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId must not be null");
        this.deviceName = deviceName;
        this.refreshTokenHash = refreshTokenHash;
        this.status = SeniorDeviceStatus.ACTIVE;
        this.lastUsedAt = LocalDateTime.now();
    }

    public void updateRefreshToken(final String refreshTokenHash) {
        this.refreshTokenHash = Objects.requireNonNull(refreshTokenHash, "refreshTokenHash must not be null");
        this.lastUsedAt = LocalDateTime.now();
    }

    public void revoke() {
        this.status = SeniorDeviceStatus.REVOKED;
        this.revokedAt = LocalDateTime.now();
        this.refreshTokenHash = null;
    }

    public boolean isActive() {
        return this.status == SeniorDeviceStatus.ACTIVE;
    }
}
