package com.ppiyaki.medication;

/**
 * 보호자 대시보드 일자 단위 인증 상태.
 * spec docs/features/caregiver-dashboard.md §5-2.
 */
public enum DayStatus {
    PERFECT,
    DELAYED,
    MISSED,
    PENDING,
    FUTURE
}
