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
import java.time.LocalDate;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_slot", nullable = false, length = 16)
    private MealSlot mealSlot;

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
            final MealSlot mealSlot,
            final String dosage,
            final String daysOfWeek,
            final LocalDate startDate,
            final LocalDate endDate
    ) {
        this.medicineId = Objects.requireNonNull(medicineId, "medicineId must not be null");
        this.mealSlot = Objects.requireNonNull(mealSlot, "mealSlot must not be null");
        this.dosage = Objects.requireNonNull(dosage, "dosage must not be null");
        this.daysOfWeek = daysOfWeek;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void update(
            final MealSlot mealSlot,
            final String dosage,
            final String daysOfWeek,
            final LocalDate startDate,
            final LocalDate endDate
    ) {
        if (mealSlot != null) {
            this.mealSlot = mealSlot;
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
