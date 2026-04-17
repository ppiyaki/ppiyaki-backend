package com.ppiyaki.medication.controller;

import com.ppiyaki.medication.controller.dto.ScheduleCreateRequest;
import com.ppiyaki.medication.controller.dto.ScheduleListResponse;
import com.ppiyaki.medication.controller.dto.ScheduleResponse;
import com.ppiyaki.medication.controller.dto.ScheduleUpdateRequest;
import com.ppiyaki.medication.service.MedicationScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/medicines/{medicineId}/schedules")
public class MedicationScheduleController {

    private final MedicationScheduleService medicationScheduleService;

    public MedicationScheduleController(final MedicationScheduleService medicationScheduleService) {
        this.medicationScheduleService = medicationScheduleService;
    }

    @PostMapping
    public ResponseEntity<ScheduleResponse> create(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long medicineId,
            @Valid @RequestBody final ScheduleCreateRequest scheduleCreateRequest
    ) {
        final ScheduleResponse scheduleResponse = medicationScheduleService.create(
                userId, medicineId, scheduleCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleResponse);
    }

    @GetMapping
    public ResponseEntity<ScheduleListResponse> readAll(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long medicineId
    ) {
        final List<ScheduleResponse> responses = medicationScheduleService.readAll(userId, medicineId);
        final ScheduleListResponse scheduleListResponse = ScheduleListResponse.from(responses);
        return ResponseEntity.ok(scheduleListResponse);
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<ScheduleResponse> readById(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long medicineId,
            @PathVariable final Long scheduleId
    ) {
        final ScheduleResponse scheduleResponse = medicationScheduleService.readById(
                userId, medicineId, scheduleId);
        return ResponseEntity.ok(scheduleResponse);
    }

    @PatchMapping("/{scheduleId}")
    public ResponseEntity<ScheduleResponse> update(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long medicineId,
            @PathVariable final Long scheduleId,
            @Valid @RequestBody final ScheduleUpdateRequest scheduleUpdateRequest
    ) {
        final ScheduleResponse scheduleResponse = medicationScheduleService.update(
                userId, medicineId, scheduleId, scheduleUpdateRequest);
        return ResponseEntity.ok(scheduleResponse);
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long medicineId,
            @PathVariable final Long scheduleId
    ) {
        medicationScheduleService.delete(userId, medicineId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
