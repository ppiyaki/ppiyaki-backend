package com.ppiyaki.medication.controller.dto;

import com.ppiyaki.medication.LogStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MedicationLogUpsertRequest(
        @NotNull Long scheduleId,
        @NotNull LocalDate targetDate,
        LocalDateTime takenAt,
        @NotNull LogStatus status,
        String photoObjectKey
) {
}
