package com.ppiyaki.medication.controller.dto;

import java.util.List;

public record MedicationLogListResponse(
        List<MedicationLogResponse> responses
) {

    public static MedicationLogListResponse from(final List<MedicationLogResponse> responses) {
        return new MedicationLogListResponse(responses);
    }
}
