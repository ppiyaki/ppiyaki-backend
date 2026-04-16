package com.ppiyaki.medication.controller.dto;

import com.ppiyaki.medication.MedicationSchedule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ScheduleResponse(
        Long id,
        Long medicineId,
        LocalTime scheduledTime,
        String dosage,
        String daysOfWeek,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt
) {

    public static ScheduleResponse from(final MedicationSchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getMedicineId(),
                schedule.getScheduledTime(),
                schedule.getDosage(),
                schedule.getDaysOfWeek(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.getCreatedAt()
        );
    }
}
