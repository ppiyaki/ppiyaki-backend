package com.ppiyaki.user.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ppiyaki.user.User;
import java.time.LocalTime;

public record MealTimesResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss") LocalTime breakfast,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss") LocalTime lunch,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss") LocalTime dinner
) {

    public static MealTimesResponse from(final User user) {
        if (user.getBreakfastTime() == null
                && user.getLunchTime() == null
                && user.getDinnerTime() == null) {
            return null;
        }
        return new MealTimesResponse(user.getBreakfastTime(), user.getLunchTime(), user.getDinnerTime());
    }
}
