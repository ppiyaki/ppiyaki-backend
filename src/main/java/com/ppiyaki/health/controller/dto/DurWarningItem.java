package com.ppiyaki.health.controller.dto;

public record DurWarningItem(
        String type,
        String withMedicine,
        String severity,
        String description,
        String rawText
) {
}
