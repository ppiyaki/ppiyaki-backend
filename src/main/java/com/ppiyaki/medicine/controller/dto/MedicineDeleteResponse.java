package com.ppiyaki.medicine.controller.dto;

import java.util.Objects;

public record MedicineDeleteResponse(
        Long deletedMedicineId,
        int deletedScheduleCount
) {

    public MedicineDeleteResponse {
        Objects.requireNonNull(deletedMedicineId, "deletedMedicineId must not be null");
    }
}
