package com.ppiyaki.medicine.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MedicineCreateRequest(
        Long seniorId,
        @NotBlank String name,
        @NotNull Integer totalAmount,
        @NotNull Integer remainingAmount,
        String durWarningText
) {
}
