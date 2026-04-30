package com.ppiyaki.medication.controller;

import com.ppiyaki.medication.controller.dto.MedicationLogListResponse;
import com.ppiyaki.medication.controller.dto.MedicationLogResponse;
import com.ppiyaki.medication.controller.dto.MedicationLogUpsertRequest;
import com.ppiyaki.medication.service.MedicationLogService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/medication-logs")
@ConditionalOnProperty(prefix = "ncp.storage", name = "bucket-name")
public class MedicationLogController {

    private final MedicationLogService medicationLogService;

    public MedicationLogController(final MedicationLogService medicationLogService) {
        this.medicationLogService = medicationLogService;
    }

    @PutMapping
    public ResponseEntity<MedicationLogResponse> upsert(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final MedicationLogUpsertRequest request
    ) {
        return ResponseEntity.ok(medicationLogService.upsert(userId, request));
    }

    @GetMapping
    public ResponseEntity<MedicationLogListResponse> readByPeriod(
            @AuthenticationPrincipal final Long userId,
            @RequestParam(required = false) final Long seniorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate to
    ) {
        return ResponseEntity.ok(medicationLogService.readByPeriod(userId, seniorId, from, to));
    }
}
