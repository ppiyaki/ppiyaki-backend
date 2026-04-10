package com.ppiyaki.medicine.controller;

import com.ppiyaki.medicine.controller.dto.MedicineCreateRequest;
import com.ppiyaki.medicine.controller.dto.MedicineDeleteResponse;
import com.ppiyaki.medicine.controller.dto.MedicineListResponse;
import com.ppiyaki.medicine.controller.dto.MedicineResponse;
import com.ppiyaki.medicine.controller.dto.MedicineUpdateRequest;
import com.ppiyaki.medicine.service.MedicineService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/medicines")
public class MedicineController {

    private final MedicineService medicineService;

    public MedicineController(final MedicineService medicineService) {
        this.medicineService = Objects.requireNonNull(medicineService, "medicineService must not be null");
    }

    @PostMapping
    public ResponseEntity<MedicineResponse> create(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final MedicineCreateRequest medicineCreateRequest
    ) {
        final MedicineResponse medicineResponse = medicineService.create(userId, medicineCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(medicineResponse);
    }

    @GetMapping
    public ResponseEntity<MedicineListResponse> readAll(
            @AuthenticationPrincipal final Long userId,
            @RequestParam(required = false) final Long seniorId
    ) {
        final List<MedicineResponse> responses = medicineService.readAll(userId, seniorId);
        final MedicineListResponse medicineListResponse = MedicineListResponse.from(responses);
        return ResponseEntity.ok(medicineListResponse);
    }

    @GetMapping("/{medicineId}")
    public ResponseEntity<MedicineResponse> readById(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long medicineId
    ) {
        final MedicineResponse medicineResponse = medicineService.readById(userId, medicineId);
        return ResponseEntity.ok(medicineResponse);
    }

    @PatchMapping("/{medicineId}")
    public ResponseEntity<MedicineResponse> update(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long medicineId,
            @RequestBody final MedicineUpdateRequest medicineUpdateRequest
    ) {
        final MedicineResponse medicineResponse = medicineService.update(userId, medicineId, medicineUpdateRequest);
        return ResponseEntity.ok(medicineResponse);
    }

    @DeleteMapping("/{medicineId}")
    public ResponseEntity<MedicineDeleteResponse> delete(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long medicineId
    ) {
        final MedicineDeleteResponse medicineDeleteResponse = medicineService.delete(userId, medicineId);
        return ResponseEntity.ok(medicineDeleteResponse);
    }
}
