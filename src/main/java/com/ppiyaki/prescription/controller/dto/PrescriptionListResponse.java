package com.ppiyaki.prescription.controller.dto;

import com.ppiyaki.prescription.Prescription;
import java.time.LocalDateTime;
import java.util.List;

public record PrescriptionListResponse(List<PrescriptionSummary> responses) {

    public static PrescriptionListResponse from(final List<Prescription> prescriptions) {
        return new PrescriptionListResponse(
                prescriptions.stream()
                        .map(p -> new PrescriptionSummary(
                                p.getId(), p.getStatus().name(), p.getCreatedAt()))
                        .toList()
        );
    }

    public record PrescriptionSummary(
            Long id,
            String status,
            LocalDateTime createdAt
    ) {
    }
}
