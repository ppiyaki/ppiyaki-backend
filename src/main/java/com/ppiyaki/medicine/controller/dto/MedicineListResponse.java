package com.ppiyaki.medicine.controller.dto;

import java.util.List;

public record MedicineListResponse(
        List<MedicineResponse> responses
) {

    public static MedicineListResponse from(final List<MedicineResponse> responses) {
        return new MedicineListResponse(responses);
    }
}
