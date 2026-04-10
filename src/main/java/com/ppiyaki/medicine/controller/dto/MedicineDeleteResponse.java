package com.ppiyaki.medicine.controller.dto;

public record MedicineDeleteResponse(
        Long deletedMedicineId,
        int deletedScheduleCount
) {
}
