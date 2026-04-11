package com.ppiyaki.medication.controller.dto;

import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleUpdateRequest(
        LocalTime scheduledTime,
        String dosage,
        @Pattern(
                regexp = "^(DAILY|(?:MON|TUE|WED|THU|FRI|SAT|SUN)(?:,(?:MON|TUE|WED|THU|FRI|SAT|SUN))*)$",
                message = "daysOfWeek must be DAILY or comma-separated weekday codes"
        ) String daysOfWeek,
        LocalDate startDate,
        LocalDate endDate
) {
}
