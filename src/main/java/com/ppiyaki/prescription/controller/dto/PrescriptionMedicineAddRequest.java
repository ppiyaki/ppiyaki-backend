package com.ppiyaki.prescription.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record PrescriptionMedicineAddRequest(
        @NotBlank String itemSeq,
        @NotBlank String itemName,
        String dosage,
        String schedule
) {
}
