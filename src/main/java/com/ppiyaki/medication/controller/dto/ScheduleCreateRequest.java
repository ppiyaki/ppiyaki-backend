package com.ppiyaki.medication.controller.dto;

import com.ppiyaki.medication.domain.MealSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ScheduleCreateRequest(
        @NotNull MealSlot mealSlot,
        @NotNull BigDecimal dosageQuantity,
        String dosageUnit,
        @Pattern(
                regexp = "^(DAILY|(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*)$", message = "daysOfWeek must be 'DAILY' or a comma-separated list of MON,TUE,WED,THU,FRI,SAT,SUN"
        ) String daysOfWeek,
        LocalDate startDate,
        LocalDate endDate
) {
}
