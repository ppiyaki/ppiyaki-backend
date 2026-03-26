package com.ppiyaki.medication;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.ppiyaki.common.entity.CreatedTimeEntity;

@Entity
@Getter
@Table(name = "medication_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationLog extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_id")
    private Long seniorId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(name = "status")
    private String status;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "ai_status")
    private String aiStatus;

    @Column(name = "is_proxy")
    private Boolean isProxy;
    
    public MedicationLog(
            final Long seniorId, 
            final Long scheduleId, 
            final LocalDate targetDate, 
            final LocalDateTime takenAt, 
            final String status, 
            final String photoUrl, 
            final String aiStatus, 
            final Boolean isProxy
    ) {
        this.seniorId = seniorId;
        this.scheduleId = scheduleId;
        this.targetDate = targetDate;
        this.takenAt = takenAt;
        this.status = status;
        this.photoUrl = photoUrl;
        this.aiStatus = aiStatus;
        this.isProxy = isProxy;
    }
}
