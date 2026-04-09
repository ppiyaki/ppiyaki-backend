package com.ppiyaki.medication;

import com.ppiyaki.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "medication_reminders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationReminder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "senior_id", nullable = false)
    private Long seniorId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    private DeliveryStatus deliveryStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private ReminderChannel channel;

    @Column(name = "error_message")
    private String errorMessage;

    public MedicationReminder(
            final Long scheduleId,
            final Long seniorId,
            final LocalDate targetDate,
            final LocalDateTime scheduledAt,
            final DeliveryStatus deliveryStatus,
            final ReminderChannel channel) {
        this.scheduleId = scheduleId;
        this.seniorId = seniorId;
        this.targetDate = targetDate;
        this.scheduledAt = scheduledAt;
        this.deliveryStatus = deliveryStatus;
        this.channel = channel;
    }

    public void markSent(final LocalDateTime now) {
        this.sentAt = now;
        this.deliveryStatus = DeliveryStatus.SENT;
    }

    public void markFailed(final String reason) {
        this.deliveryStatus = DeliveryStatus.FAILED;
        this.errorMessage = reason;
    }
}
