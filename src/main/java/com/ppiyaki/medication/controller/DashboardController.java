package com.ppiyaki.medication.controller;

import com.ppiyaki.medication.controller.dto.dashboard.DailyDashboardResponse;
import com.ppiyaki.medication.controller.dto.dashboard.MonthlyDashboardResponse;
import com.ppiyaki.medication.controller.dto.dashboard.WeeklyDashboardResponse;
import com.ppiyaki.medication.service.DashboardService;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호자 대시보드 통합 조회 엔드포인트.
 * spec docs/features/caregiver-dashboard.md.
 */
@RestController
@RequestMapping("/api/v1/seniors/{seniorId}/dashboard")
@ConditionalOnProperty(prefix = "ncp.storage", name = "bucket-name")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(final DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/daily")
    public ResponseEntity<DailyDashboardResponse> getDaily(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long seniorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date
    ) {
        return ResponseEntity.ok(dashboardService.getDaily(userId, seniorId, date));
    }

    @GetMapping("/weekly")
    public ResponseEntity<WeeklyDashboardResponse> getWeekly(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long seniorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate weekStart
    ) {
        return ResponseEntity.ok(dashboardService.getWeekly(userId, seniorId, weekStart));
    }

    @GetMapping("/monthly")
    public ResponseEntity<MonthlyDashboardResponse> getMonthly(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long seniorId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") final YearMonth yearMonth
    ) {
        return ResponseEntity.ok(dashboardService.getMonthly(userId, seniorId, yearMonth));
    }
}
