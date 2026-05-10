package com.ppiyaki.pet;

import java.util.Objects;

public enum BadgeType {

    FIRST_STEP("천리길도 한 걸음부터", "첫 복약 완료"),
    MIRACLE_MORNING("진정한 미라클 모닝", "7일 연속 아침 약 정시 복용"),
    FAMILY_LINK("가족 연결고리", "보호자 연동 + 첫 안부 알림 수신"),
    HEALTH_GUARDIAN("건강 수호자", "한 달간 100% 복약 달성"),
    BUDDY("삐약이 단짝", "AI 음성 대화 5회 이상");

    private final String displayName;
    private final String description;

    BadgeType(final String displayName, final String description) {
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
