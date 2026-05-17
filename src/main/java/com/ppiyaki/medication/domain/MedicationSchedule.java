package com.ppiyaki.medication.domain;

import com.ppiyaki.common.entity.CreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

    @Column(name = "medicine_id", nullable = false)
    private Long medicineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_slot", nullable = false, length = 16)
    private MealSlot mealSlot;

    @Column(name = "dosage_quantity", precision = 5, scale = 2)
    private BigDecimal dosageQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "dosage_unit", length = 16)
    private DosageUnit dosageUnit;

    @Column(name = "days_of_week")
    private String daysOfWeek;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    public MedicationSchedule(
            final Long medicineId,
            final MealSlot mealSlot,
            final BigDecimal dosageQuantity,
            final DosageUnit dosageUnit,
            final String daysOfWeek,
            final LocalDate startDate,
            final LocalDate endDate
    ) {
        this.medicineId = Objects.requireNonNull(medicineId, "medicineId must not be null");
        this.mealSlot = Objects.requireNonNull(mealSlot, "mealSlot must not be null");
        this.dosageQuantity = dosageQuantity;
        this.dosageUnit = dosageUnit;
        this.daysOfWeek = daysOfWeek;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void update(
            final MealSlot mealSlot,
            final BigDecimal dosageQuantity,
            final DosageUnit dosageUnit,
            final String daysOfWeek,
            final LocalDate startDate,
            final LocalDate endDate
    ) {
        if (mealSlot != null) {
            this.mealSlot = mealSlot;
        }
        if (dosageQuantity != null) {
            this.dosageQuantity = dosageQuantity;
        }
        if (dosageUnit != null) {
            if (dosageUnit == DosageUnit.PRN) {
                this.dosageQuantity = null;
            }
            this.dosageUnit = dosageUnit;
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

    public String composeDosageText() {
        if (this.dosageQuantity == null && this.dosageUnit == null) {
            return null;
        }
        final String quantityText = this.dosageQuantity != null
                ? this.dosageQuantity.stripTrailingZeros().toPlainString() : "";
        final String unitText = this.dosageUnit != null ? this.dosageUnit.getDisplayValue() : "";
        return quantityText + unitText;
    }
}
