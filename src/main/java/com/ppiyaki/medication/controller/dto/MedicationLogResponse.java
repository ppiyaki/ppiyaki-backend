package com.ppiyaki.medication.controller.dto;

import com.ppiyaki.medication.LogAiStatus;
import com.ppiyaki.medication.LogStatus;
import com.ppiyaki.medication.MedicationLog;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MedicationLogResponse(
        Long id,
        Long seniorId,
        Long scheduleId,
        LocalDate targetDate,
        LocalDateTime takenAt,
        LogStatus status,
        String photoUrl,
        LogAiStatus aiStatus,
        Boolean isProxy,
        Long confirmedByUserId,
        LocalDateTime createdAt
) {

    public static MedicationLogResponse from(final MedicationLog log, final String photoUrl) {
        return new MedicationLogResponse(
                log.getId(),
                log.getSeniorId(),
                log.getScheduleId(),
                log.getTargetDate(),
                log.getTakenAt(),
                log.getStatus(),
                photoUrl,
                log.getAiStatus(),
                log.getIsProxy(),
                log.getConfirmedByUserId(),
                log.getCreatedAt()
        );
    }
}
