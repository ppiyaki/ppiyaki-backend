package com.ppiyaki.prescription.controller.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record PrescriptionMedicineAddRequest(
        @NotBlank String itemSeq,
        @NotBlank String itemName,
        BigDecimal dosageQuantity,
        String dosageUnit,
        String schedule
) {
}
