package com.ppiyaki.prescription.controller.dto;

import com.ppiyaki.medication.MealSlot;
import com.ppiyaki.prescription.PrescriptionMedicineCandidate;
import java.math.BigDecimal;
import java.util.List;

public record PrescriptionMedicineCandidateResponse(
        Long id,
        String ocrRawText,
        String extractedName,
        String extractedDosage,
        BigDecimal extractedDosageQuantity,
        String extractedDosageUnit,
        String extractedSchedule,
        String matchedItemSeq,
        String matchedItemName,
        String matchType,
        String matchReason,
        String caregiverDecision,
        String caregiverChosenItemSeq,
        Long createdMedicineId,
        List<MealSlot> suggestedMealSlots,
        List<MealSlot> confirmedMealSlots
) {

    public static PrescriptionMedicineCandidateResponse from(final PrescriptionMedicineCandidate candidate) {
        return new PrescriptionMedicineCandidateResponse(
                candidate.getId(),
                candidate.getOcrRawText(),
                candidate.getExtractedName(),
                candidate.getExtractedDosage(),
                candidate.getExtractedDosageQuantity() != null
                        ? candidate.getExtractedDosageQuantity().stripTrailingZeros()
                        : null,
                candidate.getExtractedDosageUnit() != null ? candidate.getExtractedDosageUnit().getDisplayValue()
                        : null,
                candidate.getExtractedSchedule(),
                candidate.getMatchedItemSeq(),
                candidate.getMatchedItemName(),
                candidate.getMatchType() != null ? candidate.getMatchType().name() : null,
                candidate.getMatchReason(),
                candidate.getCaregiverDecision().name(),
                candidate.getCaregiverChosenItemSeq(),
                candidate.getCreatedMedicineId(),
                candidate.getSuggestedMealSlotsList(),
                candidate.getConfirmedMealSlotsList()
        );
    }
}
