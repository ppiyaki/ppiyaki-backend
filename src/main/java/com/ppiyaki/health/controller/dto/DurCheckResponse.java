package com.ppiyaki.health.controller.dto;

import com.ppiyaki.health.DurCheck;
import java.time.LocalDateTime;
import java.util.List;

public record DurCheckResponse(
        Long id,
        Long medicineId,
        LocalDateTime checkedAt,
        String warningLevel,
        String warningText,
        List<DurWarningItem> warnings,
        boolean fromCache
) {

    public static DurCheckResponse from(final DurCheck durCheck,
            final List<DurWarningItem> warnings, final boolean fromCache) {
        return new DurCheckResponse(
                durCheck.getId(),
                durCheck.getMedicineId(),
                durCheck.getCheckedAt(),
                durCheck.getWarningLevel() != null ? durCheck.getWarningLevel().name() : null,
                durCheck.getWarningText(),
                warnings,
                fromCache
        );
    }
}
