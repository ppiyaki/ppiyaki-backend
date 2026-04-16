package com.ppiyaki.prescription.controller.dto;

import com.ppiyaki.prescription.Prescription;
import com.ppiyaki.prescription.PrescriptionMedicineCandidate;
import java.time.LocalDateTime;
import java.util.List;

public record PrescriptionDetailResponse(
        Long id,
        Long ownerId,
        String status,
        String maskedImageObjectKey,
        String failureReason,
        List<PrescriptionMedicineCandidateResponse> candidates,
        LocalDateTime createdAt
) {

    public static PrescriptionDetailResponse from(
            final Prescription prescription,
            final List<PrescriptionMedicineCandidate> candidates
    ) {
        return new PrescriptionDetailResponse(
                prescription.getId(),
                prescription.getOwnerId(),
                prescription.getStatus().name(),
                prescription.getMaskedImageObjectKey(),
                prescription.getFailureReason(),
                candidates.stream()
                        .map(PrescriptionMedicineCandidateResponse::from)
                        .toList(),
                prescription.getCreatedAt()
        );
    }
}
