package com.ppiyaki.prescription.controller.dto;

import com.ppiyaki.medication.MealSlot;
import com.ppiyaki.prescription.CaregiverDecision;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CandidateDecisionRequest(
        @NotNull CaregiverDecision decision,
        String chosenItemSeq,
        List<MealSlot> confirmedMealSlots,
        String dosage
) {
}
