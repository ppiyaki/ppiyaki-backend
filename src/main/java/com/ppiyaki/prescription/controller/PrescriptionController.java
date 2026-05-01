package com.ppiyaki.prescription.controller;

import com.ppiyaki.prescription.PrescriptionStatus;
import com.ppiyaki.prescription.controller.dto.CandidateDecisionRequest;
import com.ppiyaki.prescription.controller.dto.PrescriptionCreateRequest;
import com.ppiyaki.prescription.controller.dto.PrescriptionDetailResponse;
import com.ppiyaki.prescription.controller.dto.PrescriptionListResponse;
import com.ppiyaki.prescription.controller.dto.PrescriptionMedicineAddRequest;
import com.ppiyaki.prescription.service.PrescriptionService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prescriptions")
@ConditionalOnProperty(prefix = "clova.ocr", name = "secret")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    public PrescriptionController(final PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    @PostMapping
    public ResponseEntity<PrescriptionDetailResponse> create(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final PrescriptionCreateRequest request
    ) {
        final PrescriptionDetailResponse response = prescriptionService.processAndCreate(userId, request.objectKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{prescriptionId}")
    public ResponseEntity<PrescriptionDetailResponse> getDetail(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long prescriptionId
    ) {
        return ResponseEntity.ok(prescriptionService.getDetail(userId, prescriptionId));
    }

    @GetMapping
    public ResponseEntity<PrescriptionListResponse> list(
            @AuthenticationPrincipal final Long userId,
            @RequestParam(required = false) final PrescriptionStatus status
    ) {
        return ResponseEntity.ok(prescriptionService.listByOwner(userId, status));
    }

    @PatchMapping("/{prescriptionId}/medicines/{candidateId}")
    public ResponseEntity<Void> updateCandidateDecision(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long prescriptionId,
            @PathVariable final Long candidateId,
            @Valid @RequestBody final CandidateDecisionRequest request
    ) {
        prescriptionService.updateCandidateDecision(userId, prescriptionId, candidateId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{prescriptionId}/medicines")
    public ResponseEntity<PrescriptionDetailResponse> addManualMedicine(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long prescriptionId,
            @Valid @RequestBody final PrescriptionMedicineAddRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(prescriptionService.addManualMedicine(userId, prescriptionId, request));
    }

    @PostMapping("/{prescriptionId}/confirm")
    public ResponseEntity<PrescriptionDetailResponse> confirm(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long prescriptionId
    ) {
        return ResponseEntity.ok(prescriptionService.confirm(userId, prescriptionId));
    }

    @PostMapping("/{prescriptionId}/reject")
    public ResponseEntity<Void> reject(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long prescriptionId
    ) {
        prescriptionService.reject(userId, prescriptionId);
        return ResponseEntity.noContent().build();
    }
}
