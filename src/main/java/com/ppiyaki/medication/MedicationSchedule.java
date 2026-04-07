package com.ppiyaki.medication;

import com.ppiyaki.common.entity.CreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "medication_schedules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationSchedule extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "medicine_id")
    private Long medicineId;

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    @Column(name = "dosage")
    private String dosage;

    public MedicationSchedule(
            final Long medicineId,
            final LocalTime scheduledTime,
            final String dosage
    ) {
        this.medicineId = medicineId;
        this.scheduledTime = scheduledTime;
        this.dosage = dosage;
    }
}
