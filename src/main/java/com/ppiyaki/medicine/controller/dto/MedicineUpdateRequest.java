package com.ppiyaki.medicine.controller.dto;

public record MedicineUpdateRequest(
        String name,
        Integer totalAmount,
        Integer remainingAmount,
        String durWarningText
) {
}
