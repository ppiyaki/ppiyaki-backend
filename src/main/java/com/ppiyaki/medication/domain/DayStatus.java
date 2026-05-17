package com.ppiyaki.medication.domain;

/**
 * 보호자 대시보드 일자 단위 인증 상태.
 * spec docs/features/caregiver-dashboard.md §5-2.
 */
public enum DayStatus {
    PERFECT,
    DELAYED,
    MISSED,
    PENDING,
    FUTURE,
    /** 시니어 가입 이전 날짜 — 스케줄/로그 자체가 존재할 수 없는 시점. spec issue #326 */
    NOT_SCHEDULED
}
