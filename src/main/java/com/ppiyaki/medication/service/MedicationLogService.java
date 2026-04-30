package com.ppiyaki.medication.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.storage.PhotoUrlAssembler;
import com.ppiyaki.medication.MedicationLog;
import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.controller.dto.MedicationLogListResponse;
import com.ppiyaki.medication.controller.dto.MedicationLogResponse;
import com.ppiyaki.medication.controller.dto.MedicationLogUpsertRequest;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.repository.CareRelationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "ncp.storage", name = "bucket-name")
public class MedicationLogService {

    private static final long MAX_QUERY_RANGE_DAYS = 31L;
    private static final Pattern OBJECT_KEY_PATTERN = Pattern.compile(
            "^[a-z0-9-]+/(\\d+)/[a-zA-Z0-9-]+\\.[a-zA-Z0-9]+$");

    private final MedicationLogRepository medicationLogRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final MedicineRepository medicineRepository;
    private final CareRelationRepository careRelationRepository;
    private final PhotoUrlAssembler photoUrlAssembler;

    public MedicationLogService(
            final MedicationLogRepository medicationLogRepository,
            final MedicationScheduleRepository medicationScheduleRepository,
            final MedicineRepository medicineRepository,
            final CareRelationRepository careRelationRepository,
            final PhotoUrlAssembler photoUrlAssembler
    ) {
        this.medicationLogRepository = medicationLogRepository;
        this.medicationScheduleRepository = medicationScheduleRepository;
        this.medicineRepository = medicineRepository;
        this.careRelationRepository = careRelationRepository;
        this.photoUrlAssembler = photoUrlAssembler;
    }

    @Transactional
    public MedicationLogResponse upsert(final Long userId, final MedicationLogUpsertRequest request) {
        final MedicationSchedule schedule = medicationScheduleRepository.findById(request.scheduleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        final Medicine medicine = medicineRepository.findById(schedule.getMedicineId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDICINE_NOT_FOUND));
        final Long seniorId = medicine.getOwnerId();

        final boolean isProxy = resolveProxyFlag(userId, seniorId);

        if (request.photoObjectKey() != null && !request.photoObjectKey().isBlank()) {
            validatePhotoObjectKey(request.photoObjectKey(), userId);
        }

        final LocalDateTime takenAt = request.takenAt() != null ? request.takenAt() : LocalDateTime.now();

        final MedicationLog log = medicationLogRepository
                .findByScheduleIdAndTargetDate(request.scheduleId(), request.targetDate())
                .map(existing -> {
                    existing.updateRecord(takenAt, request.status(), request.photoObjectKey(), isProxy, userId);
                    return existing;
                })
                .orElseGet(() -> medicationLogRepository.save(new MedicationLog(
                        seniorId, request.scheduleId(), request.targetDate(),
                        takenAt, request.status(), request.photoObjectKey(), isProxy, userId)));

        return MedicationLogResponse.from(log, photoUrlAssembler.toFullUrl(log.getPhotoObjectKey()));
    }

    @Transactional(readOnly = true)
    public MedicationLogListResponse readByPeriod(
            final Long userId, final Long seniorIdParam, final LocalDate from, final LocalDate to
    ) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from and to are required");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from must be on or before to");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_QUERY_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "Query range cannot exceed " + MAX_QUERY_RANGE_DAYS + " days");
        }

        final Long seniorId = seniorIdParam != null ? seniorIdParam : userId;
        if (!userId.equals(seniorId)) {
            careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(userId, seniorId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
        }

        final List<MedicationLogResponse> responses = medicationLogRepository
                .findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(seniorId, from, to)
                .stream()
                .map(log -> MedicationLogResponse.from(log, photoUrlAssembler.toFullUrl(log.getPhotoObjectKey())))
                .toList();
        return MedicationLogListResponse.from(responses);
    }

    private boolean resolveProxyFlag(final Long userId, final Long seniorId) {
        if (userId.equals(seniorId)) {
            return false;
        }
        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(userId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
        return true;
    }

    private void validatePhotoObjectKey(final String objectKey, final Long userId) {
        if (objectKey.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid objectKey format");
        }
        final var matcher = OBJECT_KEY_PATTERN.matcher(objectKey);
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid objectKey format");
        }
        final long uploaderId = Long.parseLong(matcher.group(1));
        if (uploaderId != userId) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "objectKey owner mismatch");
        }
    }
}
