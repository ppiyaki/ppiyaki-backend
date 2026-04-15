package com.ppiyaki.medication.controller.dto;

import java.util.List;
import java.util.Objects;

public record ScheduleListResponse(
        List<ScheduleResponse> responses
) {

    public ScheduleListResponse {
        Objects.requireNonNull(responses, "responses must not be null");
    }

    public static ScheduleListResponse from(final List<ScheduleResponse> responses) {
        return new ScheduleListResponse(responses);
    }
}
