package com.ppiyaki.medicine.controller;

import com.ppiyaki.medicine.controller.dto.MedicineCandidate;
import com.ppiyaki.medicine.service.MedicineSearchService;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/medicines")
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class MedicineSearchController {

    private final MedicineSearchService medicineSearchService;

    public MedicineSearchController(final MedicineSearchService medicineSearchService) {
        this.medicineSearchService = medicineSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<MedicineSearchResponse> search(
            @RequestParam final String q,
            @RequestParam(defaultValue = "10") final int limit
    ) {
        final List<MedicineCandidate> candidates = medicineSearchService.search(q, limit);
        return ResponseEntity.ok(new MedicineSearchResponse(candidates));
    }

    public record MedicineSearchResponse(List<MedicineCandidate> responses) {
    }
}
