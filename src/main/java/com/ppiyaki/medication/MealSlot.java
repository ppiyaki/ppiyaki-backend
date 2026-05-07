package com.ppiyaki.medication;

import com.ppiyaki.user.User;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public enum MealSlot {
    BREAKFAST,
    LUNCH,
    DINNER;

    public LocalTime resolveTime(final User user) {
        Objects.requireNonNull(user, "user must not be null");
        return switch (this) {
            case BREAKFAST -> user.getBreakfastTime();
            case LUNCH -> user.getLunchTime();
            case DINNER -> user.getDinnerTime();
        };
    }

    public static List<MealSlot> parseCsv(final String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        final List<MealSlot> result = new ArrayList<>();
        for (final String token : csv.split(",")) {
            final String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(MealSlot.valueOf(trimmed));
            } catch (final IllegalArgumentException ignored) {
                // 잘못된 enum 값은 무시 (LLM 응답 정규화)
            }
        }
        return result;
    }

    public static String toCsv(final List<MealSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        return slots.stream().map(MealSlot::name).collect(Collectors.joining(","));
    }
}
