package com.ppiyaki.medicine.controller.dto;

import java.util.List;
import java.util.Objects;

public record MedicineListResponse(
        List<MedicineResponse> responses
) {

    public MedicineListResponse {
        Objects.requireNonNull(responses, "responses must not be null");
    }

    public static MedicineListResponse from(final List<MedicineResponse> responses) {
        return new MedicineListResponse(responses);
    }
}
