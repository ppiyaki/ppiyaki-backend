package com.ppiyaki.prescription.controller.dto;

import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.prescription.CaregiverDecision;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CandidateDecisionRequest(
        @NotNull CaregiverDecision decision,
        String chosenItemSeq,
        List<MealSlot> confirmedMealSlots,
        BigDecimal dosageQuantity,
        String dosageUnit
) {
}
