package com.ppiyaki.medication;

import com.ppiyaki.common.entity.CreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "medication_logs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_medication_logs_schedule_target_date", columnNames = {"schedule_id",
                "target_date"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationLog extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_id", nullable = false)
    private Long seniorId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LogStatus status;

    /**
     * `photo_url` 컬럼에 실제로는 objectKey를 저장한다 (spec medication-log §5-1, §9 Q2 결정).
     * 응답에서는 서버가 endpoint/bucket을 조립한 full URL로 변환해 내려준다.
     * 컬럼명 리네임은 별도 리팩터 이슈.
     */
    @Column(name = "photo_url")
    private String photoObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status")
    private LogAiStatus aiStatus;

    @Column(name = "is_proxy", nullable = false)
    private Boolean isProxy;

    @Column(name = "confirmed_by_user_id", nullable = false)
    private Long confirmedByUserId;

    public MedicationLog(
            final Long seniorId,
            final Long scheduleId,
            final LocalDate targetDate,
            final LocalDateTime takenAt,
            final LogStatus status,
            final String photoObjectKey,
            final Boolean isProxy,
            final Long confirmedByUserId
    ) {
        this.seniorId = Objects.requireNonNull(seniorId, "seniorId must not be null");
        this.scheduleId = Objects.requireNonNull(scheduleId, "scheduleId must not be null");
        this.targetDate = Objects.requireNonNull(targetDate, "targetDate must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.isProxy = Objects.requireNonNull(isProxy, "isProxy must not be null");
        this.confirmedByUserId = Objects.requireNonNull(confirmedByUserId, "confirmedByUserId must not be null");
        this.takenAt = takenAt;
        this.photoObjectKey = photoObjectKey;
    }

    public void updateRecord(
            final LocalDateTime takenAt,
            final LogStatus status,
            final String photoObjectKey,
            final Boolean isProxy,
            final Long confirmedByUserId
    ) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.isProxy = Objects.requireNonNull(isProxy, "isProxy must not be null");
        this.confirmedByUserId = Objects.requireNonNull(confirmedByUserId, "confirmedByUserId must not be null");
        this.takenAt = takenAt;
        this.photoObjectKey = photoObjectKey;
    }

    public void updateAiStatus(final LogAiStatus aiStatus) {
        this.aiStatus = aiStatus;
    }
}
