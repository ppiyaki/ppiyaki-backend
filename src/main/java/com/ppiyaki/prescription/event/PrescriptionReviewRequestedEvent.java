package com.ppiyaki.prescription.event;

import java.util.Objects;

public record PrescriptionReviewRequestedEvent(
        Long prescriptionId,
        Long seniorId
) {

    public PrescriptionReviewRequestedEvent {
        Objects.requireNonNull(prescriptionId, "prescriptionId must not be null");
        Objects.requireNonNull(seniorId, "seniorId must not be null");
    }
}
