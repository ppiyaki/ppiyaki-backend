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
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "notification_settings", uniqueConstraints = @UniqueConstraint(
                name = "uk_caregiver_senior", columnNames = {"caregiver_id", "senior_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettings extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId;

    @Column(name = "senior_id", nullable = false)
    private Long seniorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private NotificationMode mode;

    @Column(name = "dur_warning_enabled", nullable = false)
    private boolean durWarningEnabled;

    @Column(name = "medication_delay_enabled", nullable = false)
    private boolean medicationDelayEnabled;

    @Column(name = "medication_delay_threshold_minutes", nullable = false)
    private int medicationDelayThresholdMinutes;

    @Column(name = "family_safety_enabled", nullable = false)
    private boolean familySafetyEnabled;

    @Column(name = "family_safety_threshold_hours", nullable = false)
    private int familySafetyThresholdHours;

    @Column(name = "medication_complete_enabled", nullable = false)
    private boolean medicationCompleteEnabled;

    NotificationSettings(
            final Long caregiverId,
            final Long seniorId,
            final NotificationMode mode,
            final boolean durWarningEnabled,
            final boolean medicationDelayEnabled,
            final int medicationDelayThresholdMinutes,
            final boolean familySafetyEnabled,
            final int familySafetyThresholdHours,
            final boolean medicationCompleteEnabled
    ) {
        this.caregiverId = Objects.requireNonNull(caregiverId, "caregiverId must not be null");
        this.seniorId = Objects.requireNonNull(seniorId, "seniorId must not be null");
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.durWarningEnabled = durWarningEnabled;
        this.medicationDelayEnabled = medicationDelayEnabled;
        this.medicationDelayThresholdMinutes = medicationDelayThresholdMinutes;
        this.familySafetyEnabled = familySafetyEnabled;
        this.familySafetyThresholdHours = familySafetyThresholdHours;
        this.medicationCompleteEnabled = medicationCompleteEnabled;
    }

    public static NotificationSettings createWithStandardPreset(final Long caregiverId, final Long seniorId) {
        return new NotificationSettings(
                caregiverId,
                seniorId,
                NotificationMode.STANDARD,
                true,
                true,
                60,
                true,
                48,
                false
        );
    }

    public static NotificationSettings createWithIntensivePreset(final Long caregiverId, final Long seniorId) {
        return new NotificationSettings(
                caregiverId,
                seniorId,
                NotificationMode.INTENSIVE,
                true,
                true,
                30,
                true,
                12,
                true
        );
    }

    public void applyStandardPreset() {
        this.mode = NotificationMode.STANDARD;
        this.durWarningEnabled = true;
        this.medicationDelayEnabled = true;
        this.medicationDelayThresholdMinutes = 60;
        this.familySafetyEnabled = true;
        this.familySafetyThresholdHours = 48;
        this.medicationCompleteEnabled = false;
    }

    public void applyIntensivePreset() {
        this.mode = NotificationMode.INTENSIVE;
        this.durWarningEnabled = true;
        this.medicationDelayEnabled = true;
        this.medicationDelayThresholdMinutes = 30;
        this.familySafetyEnabled = true;
        this.familySafetyThresholdHours = 12;
        this.medicationCompleteEnabled = true;
    }
}
