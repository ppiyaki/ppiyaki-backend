package com.ppiyaki.notification;

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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "notifications", indexes = @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at"), uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_dedup", columnNames = {"user_id", "category", "senior_id", "target_date",
                        "meal_slot", "schedule_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "senior_id")
    private Long seniorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private NotificationCategory category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "payload", columnDefinition = "JSON")
    private String payload;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "meal_slot", length = 16)
    private String mealSlot;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    Notification(
            final Long userId,
            final Long seniorId,
            final NotificationCategory category,
            final String title,
            final String body,
            final String payload,
            final LocalDate targetDate,
            final String mealSlot,
            final Long scheduleId
    ) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.body = Objects.requireNonNull(body, "body must not be null");
        this.seniorId = seniorId;
        this.payload = payload;
        this.targetDate = targetDate;
        this.mealSlot = mealSlot;
        this.scheduleId = scheduleId;
    }

    public static Notification createForMedicationReminder(
            final Long seniorUserId,
            final String title,
            final String body,
            final LocalDate targetDate,
            final String mealSlot
    ) {
        return new Notification(
                seniorUserId,
                null,
                NotificationCategory.MEDICATION_REMINDER,
                title,
                body,
                null,
                targetDate,
                mealSlot,
                null
        );
    }

    public boolean isRead() {
        return this.readAt != null;
    }

    public void markAsRead(final LocalDateTime readAt) {
        if (this.readAt == null) {
            this.readAt = Objects.requireNonNull(readAt, "readAt must not be null");
        }
    }
}
