package com.ppiyaki.medication.controller.dto.dashboard;

import com.ppiyaki.medication.DayStatus;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 월간 대시보드 응답 — 캘린더 색상용.
 * spec docs/features/caregiver-dashboard.md §5-3.
 *
 * <p>일자별 dayStatus만 포함. 슬롯/medicine/photo는 frontend가 일자 클릭 시 daily endpoint로 가져간다.
 */
public record MonthlyDashboardResponse(
        Long seniorId,
        YearMonth yearMonth,
        List<DayEntry> days
) {

    public record DayEntry(
            LocalDate date,
            DayStatus dayStatus
    ) {
    }
}
