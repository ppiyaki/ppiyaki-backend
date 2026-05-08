package com.ppiyaki.medication.event;

import java.util.Objects;

public record MedicationTakenEvent(
        Long seniorId
) {

    public MedicationTakenEvent {
        Objects.requireNonNull(seniorId, "seniorId must not be null");
    }
}
