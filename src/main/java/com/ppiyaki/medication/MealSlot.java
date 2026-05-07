package com.ppiyaki.medication;

import com.ppiyaki.user.User;
import java.time.LocalTime;
import java.util.Objects;

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
}
