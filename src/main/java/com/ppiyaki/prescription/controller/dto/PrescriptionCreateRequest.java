package com.ppiyaki.prescription.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record PrescriptionCreateRequest(
        @NotBlank String objectKey
) {
}
