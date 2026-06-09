package com.ppiyaki.medication.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.infrastructure.ai.OpenAiClient;
import com.ppiyaki.infrastructure.storage.NcpStorageProperties;
import com.ppiyaki.infrastructure.storage.PhotoUrlAssembler;
import com.ppiyaki.medication.controller.dto.MedicationLogListResponse;
import com.ppiyaki.medication.controller.dto.MedicationLogResponse;
import com.ppiyaki.medication.controller.dto.MedicationLogUpsertRequest;
import com.ppiyaki.medication.domain.LogPillCountStatus;
import com.ppiyaki.medication.domain.LogStatus;
import com.ppiyaki.medication.domain.MedicationLog;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
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
    private final UserRepository userRepository;
    private final PhotoUrlAssembler photoUrlAssembler;
    private final OpenAiClient openAiClient;
    private final NcpStorageProperties storageProperties;
    private final S3Client s3Client;
    private final ApplicationEventPublisher eventPublisher;
    private final com.ppiyaki.notification.repository.NotificationRepository notificationRepository;
    private final MeterRegistry meterRegistry;

    public MedicationLogService(
            final MedicationLogRepository medicationLogRepository,
            final MedicationScheduleRepository medicationScheduleRepository,
            final MedicineRepository medicineRepository,
            final CareRelationRepository careRelationRepository,
            final UserRepository userRepository,
            final PhotoUrlAssembler photoUrlAssembler,
            final OpenAiClient openAiClient,
            final NcpStorageProperties storageProperties,
            final S3Client s3Client,
            final ApplicationEventPublisher eventPublisher,
            final com.ppiyaki.notification.repository.NotificationRepository notificationRepository,
            final MeterRegistry meterRegistry
    ) {
        this.medicationLogRepository = medicationLogRepository;
        this.medicationScheduleRepository = medicationScheduleRepository;
        this.medicineRepository = medicineRepository;
        this.careRelationRepository = careRelationRepository;
        this.userRepository = userRepository;
        this.photoUrlAssembler = photoUrlAssembler;
        this.openAiClient = openAiClient;
        this.storageProperties = storageProperties;
        this.s3Client = s3Client;
        this.eventPublisher = eventPublisher;
        this.notificationRepository = notificationRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public MedicationLogResponse upsert(final Long userId, final MedicationLogUpsertRequest request) {
        final MedicationSchedule schedule = medicationScheduleRepository.findById(request.scheduleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        final Medicine medicine = medicineRepository.findById(schedule.getMedicineId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDICINE_NOT_FOUND));
        final Long seniorId = medicine.getOwnerId();

        final boolean isProxy = resolveProxyFlag(userId, seniorId);

        final User senior = userRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (senior.getCareMode() == CareMode.MANAGED
                && request.status() == LogStatus.TAKEN
                && (request.photoObjectKey() == null || request.photoObjectKey().isBlank())) {
            throw new BusinessException(ErrorCode.MEDICATION_LOG_PHOTO_REQUIRED);
        }

        if (request.photoObjectKey() != null && !request.photoObjectKey().isBlank()) {
            validatePhotoObjectKey(request.photoObjectKey(), userId);
        }

        final LocalDateTime takenAt = request.takenAt() != null ? request.takenAt() : LocalDateTime.now();

        final Optional<MedicationLog> existing = medicationLogRepository
                .findByScheduleIdAndTargetDate(request.scheduleId(), request.targetDate());
        final LogStatus previousStatus = existing.map(MedicationLog::getStatus).orElse(null);

        final MedicationLog log;
        try {
            log = existing
                    .map(found -> {
                        found.updateRecord(takenAt, request.status(), request.photoObjectKey(), isProxy, userId);
                        return found;
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

        // 새로 TAKEN으로 전환되는 케이스에만 잔여분 차감 (멱등성: 이미 TAKEN이던 row 재호출 시 중복 차감 방지).
        // 차감 단위 = schedule.dosageQuantity (BigDecimal). null이면 차감 skip
        // (옛 schedule이거나 PRN 같은 정의 안 된 케이스. spec dosage-quantity-unit-split.md Q7 결정).
        if (request.status() == LogStatus.TAKEN && previousStatus != LogStatus.TAKEN) {
            final BigDecimal dosageQuantity = schedule.getDosageQuantity();
            if (dosageQuantity != null) {
                final int dosageCount = dosageQuantity.setScale(0, java.math.RoundingMode.CEILING).intValueExact();
                if (dosageCount > 0) {
                    medicine.decreaseRemainingAmount(dosageCount);
                }
            }
        }

        // 복약 성공 처리
        if (request.status() == LogStatus.TAKEN) {
            // 이벤트는 처음 TAKEN 전환 시에만 발행 (중복 포인트/뱃지 방지)
            if (previousStatus != LogStatus.TAKEN) {
                eventPublisher.publishEvent(new MedicationTakenEvent(seniorId, request.targetDate()));
            }
            // 알림 완료 처리는 로그 상태와 무관하게(이미 TAKEN이어도 알림이 미완료면) 시도 (멱등성 보장).
            // 시니어 본인 인증/보호자 대리 인증 모두 시니어의 알림이 대상 (userId=seniorId).
            notificationRepository.markReminderTaken(
                    seniorId, request.targetDate(), schedule.getMealSlot().name(), takenAt);
        }

        // Phase 2: 사진 + status=TAKEN일 때 약 개수 AI 검증 (spec medication-log-phase2 §5-4)
        if (request.status() == LogStatus.TAKEN) {
            if (request.photoObjectKey() != null && !request.photoObjectKey().isBlank()) {
                final LogPillCountStatus pillCountStatus = verifyPillCount(seniorId, schedule, request);
                log.updatePillCountStatus(pillCountStatus);
                meterRegistry.counter("ppiyaki.medication.pill_count.total",
                        "result", pillCountStatus.name()).increment();
                // COUNT_MATCH일 때 슬롯의 다른 active schedule도 TAKEN 전파 (issue #343).
                // 시니어가 슬롯 전체 약을 사진 한 장에 담아 인증한 경우 == 슬롯 전체 인증으로 인정.
                if (pillCountStatus == LogPillCountStatus.COUNT_MATCH) {
                    propagateTakenToSlotSchedules(seniorId, schedule, request, takenAt, isProxy, userId);
                }
            } else {
                // 사진 없이 매뉴얼 인증한 경우에도 슬롯 전체 인증으로 인정하여 전파
                propagateTakenToSlotSchedules(seniorId, schedule, request, takenAt, isProxy, userId);
            }
        }

        final String transition = resolveTransition(request.status(), previousStatus);
        meterRegistry.counter("ppiyaki.medication.log.upsert.total",
                "status", request.status().name(),
                "transition", transition,
                "is_proxy", String.valueOf(isProxy)).increment();

        return MedicationLogResponse.from(log, photoUrlAssembler.toFullUrl(log.getPhotoObjectKey()));
    }

    private static String resolveTransition(final LogStatus current, final LogStatus previous) {
        if (current == LogStatus.TAKEN && previous != LogStatus.TAKEN) {
            return "new_taken";
        }
        if (current == LogStatus.TAKEN && previous == LogStatus.TAKEN) {
            return "repeat";
        }
        return "other";
    }

    /**
     * 동일 슬롯의 다른 active schedule들에 TAKEN log 전파 (issue #343).
     * spec medication-log-phase2 §5-4: AI COUNT_MATCH 시 슬롯 전체 인증으로 인정.
     *
     * <p>이미 TAKEN인 row는 skip (멱등). 새로 TAKEN 전환되는 schedule의 medicine 잔여분도 차감.
     * propagated log의 pillCountStatus는 COUNT_MATCH로 마킹 (슬롯 전체가 검증된 상태이므로).
     */
    private void propagateTakenToSlotSchedules(
            final Long seniorId,
            final MedicationSchedule triggerSchedule,
            final MedicationLogUpsertRequest request,
            final LocalDateTime takenAt,
            final boolean isProxy,
            final Long recorderId
    ) {
        final List<MedicationSchedule> slotSchedules = medicationScheduleRepository
                .findActiveByOwnerAndMealSlot(
                        seniorId, request.targetDate(), triggerSchedule.getMealSlot());
        for (final MedicationSchedule peer : slotSchedules) {
            if (peer.getId().equals(triggerSchedule.getId())) {
                continue;
            }
            final Optional<MedicationLog> existing = medicationLogRepository
                    .findByScheduleIdAndTargetDate(peer.getId(), request.targetDate());
            final LogStatus previousStatus = existing.map(MedicationLog::getStatus).orElse(null);
            if (previousStatus == LogStatus.TAKEN) {
                continue;
            }
            final MedicationLog peerLog = existing
                    .map(found -> {
                        found.updateRecord(takenAt, LogStatus.TAKEN, request.photoObjectKey(), isProxy, recorderId);
                        return found;
                    })
                    .orElseGet(() -> medicationLogRepository.saveAndFlush(new MedicationLog(
                            seniorId, peer.getId(), request.targetDate(),
                            takenAt, LogStatus.TAKEN, request.photoObjectKey(), isProxy, recorderId)));
            peerLog.updatePillCountStatus(LogPillCountStatus.COUNT_MATCH);

            final BigDecimal dosageQuantity = peer.getDosageQuantity();
            if (dosageQuantity != null) {
                final int dosageCount = dosageQuantity.setScale(0, java.math.RoundingMode.CEILING).intValueExact();
                if (dosageCount > 0) {
                    medicineRepository.findById(peer.getMedicineId())
                            .ifPresent(m -> m.decreaseRemainingAmount(dosageCount));
                }
            }
        }
    }

    /**
     * 동일 식사 슬롯 schedule들의 dosage 합 vs Vision 추출 개수 비교.
     * spec medication-log-phase2 §5-4.
     */
    private LogPillCountStatus verifyPillCount(
            final Long seniorId,
            final MedicationSchedule triggerSchedule,
            final MedicationLogUpsertRequest request
    ) {
        final List<MedicationSchedule> schedules = medicationScheduleRepository
                .findActiveByOwnerAndMealSlot(
                        seniorId, request.targetDate(), triggerSchedule.getMealSlot());

        int expected = 0;
        for (final MedicationSchedule s : schedules) {
            final BigDecimal quantity = s.getDosageQuantity();
            if (quantity == null) {
                return LogPillCountStatus.COUNT_UNKNOWN;
            }
            expected += quantity.setScale(0, java.math.RoundingMode.CEILING).intValueExact();
        }
        if (schedules.isEmpty()) {
            return LogPillCountStatus.COUNT_UNKNOWN;
        }

        final byte[] imageBytes;
        try {
            imageBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(storageProperties.bucketName())
                    .key(request.photoObjectKey())
                    .build()).asByteArray();
        } catch (final Exception e) {
            return LogPillCountStatus.COUNT_FAILED;
        }
        final String mediaType = guessMediaType(request.photoObjectKey());
        final Optional<Integer> actual = openAiClient.countPills(imageBytes, mediaType);
        if (actual.isEmpty()) {
            return LogPillCountStatus.COUNT_FAILED;
        }
        return actual.get() == expected ? LogPillCountStatus.COUNT_MATCH : LogPillCountStatus.COUNT_MISMATCH;
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
