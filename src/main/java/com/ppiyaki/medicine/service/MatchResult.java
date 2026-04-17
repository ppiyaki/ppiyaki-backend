package com.ppiyaki.medicine.service;

import com.ppiyaki.medicine.controller.dto.MedicineCandidate;
import java.util.List;
import java.util.Optional;

public record MatchResult(
        MatchType matchType,
        Optional<MedicineCandidate> recommended,
        List<MedicineCandidate> candidates,
        String reason
) {
}
