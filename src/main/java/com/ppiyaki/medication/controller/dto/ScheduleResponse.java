package com.ppiyaki.medication.controller.dto;

import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.user.domain.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ScheduleResponse(
        Long id,
        Long medicineId,
        MealSlot mealSlot,
        LocalTime scheduledTime,
        String dosage,
        BigDecimal dosageQuantity,
        String dosageUnit,
        String daysOfWeek,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt
) {

    public static ScheduleResponse from(final MedicationSchedule schedule, final User owner) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getMedicineId(),
                schedule.getMealSlot(),
                schedule.getMealSlot().resolveTime(owner),
                schedule.getDosage(),
                schedule.getDosageQuantity() != null
                        ? schedule.getDosageQuantity().stripTrailingZeros()
                        : null,
                schedule.getDosageUnit() != null ? schedule.getDosageUnit().getDisplayValue() : null,
                schedule.getDaysOfWeek(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.getCreatedAt()
        );
    }
}
