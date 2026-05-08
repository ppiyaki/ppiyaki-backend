package com.ppiyaki.medication.controller.dto.dashboard;

import com.ppiyaki.medication.DayStatus;
import com.ppiyaki.medication.MealSlot;
import com.ppiyaki.medication.SlotStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * 주간 대시보드 응답.
 * spec docs/features/caregiver-dashboard.md §5-3.
 */
public record WeeklyDashboardResponse(
        Long seniorId,
        LocalDate weekStart,
        LocalDate weekEnd,
        Double adherenceRate,
        List<DayEntry> days
) {

    public record DayEntry(
            LocalDate date,
            DayStatus dayStatus,
            List<SlotMarker> slots
    ) {
    }

    public record SlotMarker(
            MealSlot slot,
            SlotStatus status
    ) {
    }
}
