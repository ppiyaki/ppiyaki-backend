package com.ppiyaki.medication.controller.dto;

import com.ppiyaki.medication.MealSlot;
import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.user.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ScheduleResponse(
        Long id,
        Long medicineId,
        MealSlot mealSlot,
        LocalTime scheduledTime,
        String dosage,
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
                schedule.getDaysOfWeek(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.getCreatedAt()
        );
    }
}
