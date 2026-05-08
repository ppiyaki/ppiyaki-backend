package com.ppiyaki.medication;

/**
 * 보호자 대시보드 슬롯 단위 인증 상태.
 * spec docs/features/caregiver-dashboard.md §5-2.
 */
public enum SlotStatus {
    PERFECT,
    DELAYED,
    MISSED,
    PENDING,
    NOT_SCHEDULED
}
