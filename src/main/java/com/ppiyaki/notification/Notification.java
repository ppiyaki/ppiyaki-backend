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

    /**
     * dedup 자연키(senior_id, target_date, meal_slot, schedule_id) sentinel 값.
     * MySQL/H2 유니크 인덱스는 NULL을 서로 다른 값으로 취급하므로, 카테고리별로 의미 없는 컬럼에
     * NULL 대신 아래 sentinel을 채워 uk_notifications_dedup가 동시성 최종 방어선으로 동작하게 한다.
     * id는 auto-increment(1부터)라 0과, meal_slot은 실제 슬롯명(BREAKFAST 등)과, target_date는
     * 실제 복약일과 절대 충돌하지 않는다.
     */
    public static final long SENTINEL_ID = 0L;
    public static final String SENTINEL_MEAL_SLOT = "NONE";
    public static final LocalDate SENTINEL_TARGET_DATE = LocalDate.of(1900, 1, 1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "senior_id", nullable = false, columnDefinition = "bigint not null default 0")
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

    @Column(name = "target_date", nullable = false, columnDefinition = "date not null default '1900-01-01'")
    private LocalDate targetDate;

    @Column(name = "meal_slot", nullable = false, columnDefinition = "varchar(16) not null default 'NONE'")
    private String mealSlot;

    @Column(name = "schedule_id", nullable = false, columnDefinition = "bigint not null default 0")
    private Long scheduleId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

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
        // dedup 자연키 컬럼은 NULL 대신 sentinel로 채워 유니크 제약이 온전히 동작하게 한다.
        this.seniorId = seniorId != null ? seniorId : SENTINEL_ID;
        this.payload = payload;
        this.targetDate = targetDate != null ? targetDate : SENTINEL_TARGET_DATE;
        this.mealSlot = mealSlot != null ? mealSlot : SENTINEL_MEAL_SLOT;
        this.scheduleId = scheduleId != null ? scheduleId : SENTINEL_ID;
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

    public static Notification createForMedicationDelay(
            final Long caregiverId,
            final Long seniorId,
            final String title,
            final String body,
            final LocalDate targetDate,
            final String mealSlot
    ) {
        return new Notification(
                caregiverId,
                seniorId,
                NotificationCategory.MEDICATION_DELAY,
                title,
                body,
                null,
                targetDate,
                mealSlot,
                null
        );
    }

    public static Notification createForDurWarning(
            final Long caregiverId,
            final Long seniorId,
            final String title,
            final String body,
            final Long medicineId
    ) {
        return new Notification(
                caregiverId,
                seniorId,
                NotificationCategory.DUR_WARNING,
                title,
                body,
                null,
                null,
                null,
                medicineId
        );
    }

    public static Notification createForFamilySafety(
            final Long caregiverId,
            final Long seniorId,
            final String title,
            final String body,
            final LocalDate targetDate
    ) {
        return new Notification(
                caregiverId,
                seniorId,
                NotificationCategory.FAMILY_SAFETY,
                title,
                body,
                null,
                targetDate,
                null,
                null
        );
    }

    public static Notification createForMedicationComplete(
            final Long caregiverId,
            final Long seniorId,
            final String title,
            final String body,
            final LocalDate targetDate,
            final String mealSlot
    ) {
        return new Notification(
                caregiverId,
                seniorId,
                NotificationCategory.MEDICATION_COMPLETE,
                title,
                body,
                null,
                targetDate,
                mealSlot,
                null
        );
    }

    public static Notification createForPrescriptionReviewRequest(
            final Long caregiverId,
            final Long seniorId,
            final Long prescriptionId,
            final String title,
            final String body
    ) {
        // prescriptionId를 schedule_id 자연키에 저장(DUR_WARNING이 medicineId를 담는 것과 동일 패턴).
        // sentinel-only로 두면 (caregiver, senior)당 1건만 허용되어 이후 처방전 알림이 영구 차단되므로,
        // 처방전별로 dedup 되도록 prescriptionId로 자연키를 구분한다.
        return new Notification(
                caregiverId,
                seniorId,
                NotificationCategory.PRESCRIPTION_REVIEW_REQUEST,
                title,
                body,
                null,
                null,
                null,
                prescriptionId
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

    public boolean isTaken() {
        return this.takenAt != null;
    }

    /**
     * MEDICATION_REMINDER 알림이 복약 인증으로 소비됐음을 표시. 멱등 (이미 채워져 있으면 noop).
     * spec: PATCH/POST 직접 호출이 아니라 MedicationLogService.upsert TAKEN 신규 전환 분기에서 호출.
     */
    public void markTaken(final LocalDateTime takenAt) {
        if (this.takenAt == null) {
            this.takenAt = Objects.requireNonNull(takenAt, "takenAt must not be null");
        }
    }
}
