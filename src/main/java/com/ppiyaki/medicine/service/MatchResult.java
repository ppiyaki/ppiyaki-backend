package com.ppiyaki.medicine.service;

import com.ppiyaki.medicine.controller.dto.MedicineCandidate;
import java.util.List;
import java.util.Optional;

public record MatchResult(
        MatchType matchType,
        Optional<MedicineCandidate> matched,
        List<MedicineCandidate> alternatives,
        double similarity,
        String reason
) {
}
