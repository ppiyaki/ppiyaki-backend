package com.ppiyaki.medicine.controller.dto;

import com.ppiyaki.medicine.Medicine;
import java.time.LocalDateTime;

public record MedicineResponse(
        Long id,
        String name,
        Integer totalAmount,
        Integer remainingAmount,
        Long prescriptionId,
        String itemSeq,
        String durWarningText,
        LocalDateTime createdAt
) {

    public static MedicineResponse from(final Medicine medicine) {
        return new MedicineResponse(
                medicine.getId(),
                medicine.getName(),
                medicine.getTotalAmount(),
                medicine.getRemainingAmount(),
                medicine.getPrescriptionId(),
                medicine.getItemSeq(),
                medicine.getDurWarningText(),
                medicine.getCreatedAt()
        );
    }
}
