package com.ppiyaki.medication.event;

import java.time.LocalDate;
import java.util.Objects;

public record MedicationTakenEvent(
        Long seniorId,
        LocalDate targetDate
) {

    public MedicationTakenEvent {
        Objects.requireNonNull(seniorId, "seniorId must not be null");
        Objects.requireNonNull(targetDate, "targetDate must not be null");
    }
}
