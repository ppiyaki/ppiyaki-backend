package com.ppiyaki.prescription.controller.dto;

import com.ppiyaki.prescription.PrescriptionMedicineCandidate;

public record PrescriptionMedicineCandidateResponse(
        Long id,
        String ocrRawText,
        String extractedName,
        String extractedDosage,
        String extractedSchedule,
        String matchedItemSeq,
        String matchedItemName,
        String matchType,
        String matchReason,
        String caregiverDecision,
        String caregiverChosenItemSeq,
        Long createdMedicineId
) {

    public static PrescriptionMedicineCandidateResponse from(final PrescriptionMedicineCandidate candidate) {
        return new PrescriptionMedicineCandidateResponse(
                candidate.getId(),
                candidate.getOcrRawText(),
                candidate.getExtractedName(),
                candidate.getExtractedDosage(),
                candidate.getExtractedSchedule(),
                candidate.getMatchedItemSeq(),
                candidate.getMatchedItemName(),
                candidate.getMatchType() != null ? candidate.getMatchType().name() : null,
                candidate.getMatchReason(),
                candidate.getCaregiverDecision().name(),
                candidate.getCaregiverChosenItemSeq(),
                candidate.getCreatedMedicineId()
        );
    }
}
