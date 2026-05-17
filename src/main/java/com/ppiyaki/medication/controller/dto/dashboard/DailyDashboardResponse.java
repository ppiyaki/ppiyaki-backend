package com.ppiyaki.medication.controller.dto.dashboard;

import com.ppiyaki.medication.domain.DayStatus;
import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.SlotStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 일간 대시보드 응답.
 * spec docs/features/caregiver-dashboard.md §5-3.
 */
public record DailyDashboardResponse(
        Long seniorId,
        LocalDate date,
        DayStatus dayStatus,
        HeaderInfo header,
        List<SlotInfo> slots,
        List<MedicineSummary> medicines
) {

    public record HeaderInfo(
            String seniorName,
            String caregiverName,
            Integer remainingDays
    ) {
    }

    public record SlotInfo(
            MealSlot slot,
            SlotStatus status,
            LocalTime mealTime,
            LocalDateTime takenAt,
            String photoUrl,
            List<SlotMedicine> medicines
    ) {
    }

    public record SlotMedicine(
            Long medicineId,
            String name,
            String dosage
    ) {
    }

    public record MedicineSummary(
            Long medicineId,
            String name,
            List<MealSlot> slots
    ) {
    }
}
