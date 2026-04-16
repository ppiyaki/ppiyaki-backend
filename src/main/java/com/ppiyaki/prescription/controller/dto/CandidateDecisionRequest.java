package com.ppiyaki.prescription.controller.dto;

import com.ppiyaki.prescription.CaregiverDecision;
import jakarta.validation.constraints.NotNull;

public record CandidateDecisionRequest(
        @NotNull CaregiverDecision decision,
        String chosenItemSeq
) {
}
