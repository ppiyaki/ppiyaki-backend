package com.ppiyaki.medication;

import com.ppiyaki.common.entity.CreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
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

    @Column(name = "days_of_week")
    private String daysOfWeek;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    public MedicationSchedule(
            final Long medicineId,
            final LocalTime scheduledTime,
            final String dosage,
            final String daysOfWeek,
            final LocalDate startDate,
            final LocalDate endDate
    ) {
        this.medicineId = Objects.requireNonNull(medicineId, "medicineId must not be null");
        this.scheduledTime = Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
        this.dosage = Objects.requireNonNull(dosage, "dosage must not be null");
        this.daysOfWeek = daysOfWeek;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void update(
            final LocalTime scheduledTime,
            final String dosage,
            final String daysOfWeek,
            final LocalDate startDate,
            final LocalDate endDate
    ) {
        if (scheduledTime != null) {
            this.scheduledTime = scheduledTime;
        }
        if (dosage != null) {
            this.dosage = dosage;
        }
        if (daysOfWeek != null) {
            this.daysOfWeek = daysOfWeek;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        if (endDate != null) {
            this.endDate = endDate;
        }
    }
}
