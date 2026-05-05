package com.ppiyaki.medication.service;

import com.ppiyaki.common.ai.OpenAiClient;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.storage.NcpStorageProperties;
import com.ppiyaki.common.storage.PhotoUrlAssembler;
import com.ppiyaki.medication.DosageParser;
import com.ppiyaki.medication.LogAiStatus;
import com.ppiyaki.medication.LogStatus;
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Service
@ConditionalOnProperty(prefix = "ncp.storage", name = "bucket-name")
public class MedicationLogService {

    private static final long MAX_QUERY_RANGE_DAYS = 31L;
    /**
     * 복약 인증 사진 objectKey 형식 강제 (purpose 고정 + UUID 형식 + userId 세그먼트):
     * `medication-log/{userId}/{uuid}.{ext}`
     */
    private static final Pattern OBJECT_KEY_PATTERN = Pattern.compile(
            "^medication-log/(\\d+)/"
                    + "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                    + "\\.[a-zA-Z0-9]+$");

    private final MedicationLogRepository medicationLogRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final MedicineRepository medicineRepository;
    private final CareRelationRepository careRelationRepository;
    private final PhotoUrlAssembler photoUrlAssembler;
    private final OpenAiClient openAiClient;
    private final NcpStorageProperties storageProperties;
    private final S3Client s3Client;

    public MedicationLogService(
            final MedicationLogRepository medicationLogRepository,
            final MedicationScheduleRepository medicationScheduleRepository,
            final MedicineRepository medicineRepository,
            final CareRelationRepository careRelationRepository,
            final PhotoUrlAssembler photoUrlAssembler,
            final OpenAiClient openAiClient,
            final NcpStorageProperties storageProperties,
            final S3Client s3Client
    ) {
        this.medicationLogRepository = medicationLogRepository;
        this.medicationScheduleRepository = medicationScheduleRepository;
        this.medicineRepository = medicineRepository;
        this.careRelationRepository = careRelationRepository;
        this.photoUrlAssembler = photoUrlAssembler;
        this.openAiClient = openAiClient;
        this.storageProperties = storageProperties;
        this.s3Client = s3Client;
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

        final MedicationLog log;
        try {
            log = medicationLogRepository
                    .findByScheduleIdAndTargetDate(request.scheduleId(), request.targetDate())
                    .map(existing -> {
                        existing.updateRecord(takenAt, request.status(), request.photoObjectKey(), isProxy, userId);
                        return existing;
                    })
                    .orElseGet(() -> medicationLogRepository.saveAndFlush(new MedicationLog(
                            seniorId, request.scheduleId(), request.targetDate(),
                            takenAt, request.status(), request.photoObjectKey(), isProxy, userId)));
        } catch (final DataIntegrityViolationException e) {
            // 동시 INSERT 경합 발생: UNIQUE(schedule_id, target_date)에 의해 두 번째가 충돌.
            // 현재 트랜잭션은 rollback-only 상태이므로 같은 트랜잭션 내 재조회 불가.
            // 클라이언트가 재시도하면 다음 트랜잭션에서 정상 update 경로로 진입한다 (spec §5-2 멱등 보장).
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Concurrent upsert conflict on (scheduleId, targetDate); please retry");
        }

        // Phase 2: 사진 + status=TAKEN일 때 약 개수 AI 검증 (spec medication-log-phase2 §5-4)
        if (request.status() == LogStatus.TAKEN
                && request.photoObjectKey() != null && !request.photoObjectKey().isBlank()) {
            final LogAiStatus aiStatus = verifyPillCount(seniorId, schedule, request);
            log.updateAiStatus(aiStatus);
        }

        return MedicationLogResponse.from(log, photoUrlAssembler.toFullUrl(log.getPhotoObjectKey()));
    }

    /**
     * 동일 시각 schedule들의 dosage 합 vs Vision 추출 개수 비교.
     * spec medication-log-phase2 §5-4.
     */
    private LogAiStatus verifyPillCount(
            final Long seniorId,
            final MedicationSchedule triggerSchedule,
            final MedicationLogUpsertRequest request
    ) {
        final List<MedicationSchedule> schedules = medicationScheduleRepository
                .findActiveByOwnerAndScheduledTime(
                        seniorId, request.targetDate(), triggerSchedule.getScheduledTime());

        int expected = 0;
        for (final MedicationSchedule s : schedules) {
            final Optional<Integer> parsed = DosageParser.parsePillCount(s.getDosage());
            if (parsed.isEmpty()) {
                return LogAiStatus.COUNT_UNKNOWN;
            }
            expected += parsed.get();
        }
        if (schedules.isEmpty()) {
            return LogAiStatus.COUNT_UNKNOWN;
        }

        final byte[] imageBytes;
        try {
            imageBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(storageProperties.bucketName())
                    .key(request.photoObjectKey())
                    .build()).asByteArray();
        } catch (final Exception e) {
            return LogAiStatus.COUNT_FAILED;
        }
        final String mediaType = guessMediaType(request.photoObjectKey());
        final Optional<Integer> actual = openAiClient.countPills(imageBytes, mediaType);
        if (actual.isEmpty()) {
            return LogAiStatus.COUNT_FAILED;
        }
        return actual.get() == expected ? LogAiStatus.COUNT_MATCH : LogAiStatus.COUNT_MISMATCH;
    }

    private String guessMediaType(final String objectKey) {
        final String lower = objectKey.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
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
        final Matcher matcher = OBJECT_KEY_PATTERN.matcher(objectKey);
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid objectKey format");
        }
        final long uploaderId = Long.parseLong(matcher.group(1));
        if (uploaderId != userId) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "objectKey owner mismatch");
        }
    }
}
