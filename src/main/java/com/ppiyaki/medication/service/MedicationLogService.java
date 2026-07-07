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
import com.ppiyaki.notification.repository.NotificationRepository;
import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MedicationLogService.class);

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
    private final NotificationRepository notificationRepository;
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
            final NotificationRepository notificationRepository,
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
        final boolean photoProvided = request.photoObjectKey() != null && !request.photoObjectKey().isBlank();
        if (senior.getCareMode() == CareMode.MANAGED
                && request.status() == LogStatus.TAKEN
                && !photoProvided) {
            throw new BusinessException(ErrorCode.MEDICATION_LOG_PHOTO_REQUIRED);
        }

        if (photoProvided) {
            validatePhotoObjectKey(request.photoObjectKey(), userId);
        }

        // 사진 + status=TAKEN이면 DB 저장 전에 약 개수 AI 검증 (issue #462).
        // 저장 후 검증하면 COUNT_MISMATCH여도 takenAt을 되돌릴 수 없어 복약 완료로 오확정된다.
        LogPillCountStatus pillCountStatus = null;
        if (request.status() == LogStatus.TAKEN && photoProvided) {
            pillCountStatus = verifyPillCount(seniorId, schedule, request);
            meterRegistry.counter("ppiyaki.medication.pill_count.total",
                    "result", pillCountStatus.name()).increment();
        }

        // COUNT_MISMATCH면 복약 완료로 확정하지 않는다: takenAt 미설정 + status는 PENDING으로 강등.
        // 그 외(COUNT_MATCH / 사진 없는 수동 인증 / 검증 불가 COUNT_FAILED·COUNT_UNKNOWN)는 인증으로 인정.
        final boolean confirmedTaken = request.status() == LogStatus.TAKEN
                && pillCountStatus != LogPillCountStatus.COUNT_MISMATCH;
        final LogStatus effectiveStatus = request.status() == LogStatus.TAKEN && !confirmedTaken
                ? LogStatus.PENDING : request.status();
        final LocalDateTime takenAt = confirmedTaken
                ? (request.takenAt() != null ? request.takenAt() : LocalDateTime.now())
                : null;

        final Optional<MedicationLog> existing = medicationLogRepository
                .findByScheduleIdAndTargetDate(request.scheduleId(), request.targetDate());
        final LogStatus previousStatus = existing.map(MedicationLog::getStatus).orElse(null);

        final MedicationLog medicationLog;
        try {
            medicationLog = existing
                    .map(found -> {
                        found.updateRecord(takenAt, effectiveStatus, request.photoObjectKey(), isProxy, userId);
                        return found;
                    })
                    .orElseGet(() -> medicationLogRepository.saveAndFlush(new MedicationLog(
                            seniorId, request.scheduleId(), request.targetDate(),
                            takenAt, effectiveStatus, request.photoObjectKey(), isProxy, userId)));
        } catch (final DataIntegrityViolationException e) {
            // 동시 INSERT 경합 발생: UNIQUE(schedule_id, target_date)에 의해 두 번째가 충돌.
            // 현재 트랜잭션은 rollback-only 상태이므로 같은 트랜잭션 내 재조회 불가.
            // 클라이언트가 재시도하면 다음 트랜잭션에서 정상 update 경로로 진입한다 (spec §5-2 멱등 보장).
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Concurrent upsert conflict on (scheduleId, targetDate); please retry");
        }

        if (pillCountStatus != null) {
            medicationLog.updatePillCountStatus(pillCountStatus);
        }

        // 새로 TAKEN으로 확정되는 케이스에만 잔여분 차감 (멱등성: 이미 TAKEN이던 row 재호출 시 중복 차감 방지).
        // 차감 단위 = schedule.dosageQuantity (BigDecimal). null이면 차감 skip
        // (옛 schedule이거나 PRN 같은 정의 안 된 케이스. spec dosage-quantity-unit-split.md Q7 결정).
        if (confirmedTaken && previousStatus != LogStatus.TAKEN) {
            final BigDecimal dosageQuantity = schedule.getDosageQuantity();
            if (dosageQuantity != null) {
                final int dosageCount = toDosageCount(dosageQuantity);
                if (dosageCount > 0) {
                    medicine.decreaseRemainingAmount(dosageCount);
                }
            }
        }

        // 복약 성공 처리: 인증으로 확정된 경우에만 (COUNT_MISMATCH는 제외)
        if (confirmedTaken) {
            // 이벤트는 처음 TAKEN 전환 시에만 발행 (중복 포인트/뱃지 방지)
            if (previousStatus != LogStatus.TAKEN) {
                eventPublisher.publishEvent(new MedicationTakenEvent(seniorId, request.targetDate()));
            }
            // 알림 완료 처리는 로그 상태와 무관하게(이미 TAKEN이어도 알림이 미완료면) 시도 (멱등성 보장).
            // 시니어 본인 인증/보호자 대리 인증 모두 시니어의 알림이 대상 (userId=seniorId).
            notificationRepository.markReminderTaken(
                    seniorId, request.targetDate(), schedule.getMealSlot().name(), takenAt);

            // 슬롯 전체 전파: 사진 한 장에 슬롯 전체 약을 담아 인증한 COUNT_MATCH, 또는 사진 없는 수동 인증일 때만.
            // (issue #343) COUNT_FAILED·COUNT_UNKNOWN은 검증되지 않았으므로 전파하지 않는다.
            if (!photoProvided || pillCountStatus == LogPillCountStatus.COUNT_MATCH) {
                propagateTakenToSlotSchedules(seniorId, schedule, request, takenAt, isProxy, userId);
            }
        }

        final String transition = resolveTransition(request.status(), previousStatus);
        meterRegistry.counter("ppiyaki.medication.log.upsert.total",
                "status", request.status().name(),
                "transition", transition,
                "is_proxy", String.valueOf(isProxy)).increment();

        return MedicationLogResponse.from(medicationLog, photoUrlAssembler.toFullUrl(medicationLog.getPhotoObjectKey()));
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
                final int dosageCount = toDosageCount(dosageQuantity);
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
        if (schedules.isEmpty()) {
            return LogPillCountStatus.COUNT_UNKNOWN;
        }

        int expected = 0;
        for (final MedicationSchedule s : schedules) {
            final BigDecimal quantity = s.getDosageQuantity();
            if (quantity == null) {
                return LogPillCountStatus.COUNT_UNKNOWN;
            }
            expected += toDosageCount(quantity);
        }

        final byte[] imageBytes;
        try {
            imageBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(storageProperties.bucketName())
                    .key(request.photoObjectKey())
                    .build()).asByteArray();
        } catch (final Exception e) {
            log.warn("Failed to fetch medication photo from storage for pill count verification (objectKey={})",
                    request.photoObjectKey(), e);
            return LogPillCountStatus.COUNT_FAILED;
        }
        final String mediaType = guessMediaType(request.photoObjectKey());
        final Optional<Integer> actual = openAiClient.countPills(imageBytes, mediaType);
        if (actual.isEmpty()) {
            return LogPillCountStatus.COUNT_FAILED;
        }
        return actual.get() == expected ? LogPillCountStatus.COUNT_MATCH : LogPillCountStatus.COUNT_MISMATCH;
    }

    private static int toDosageCount(final BigDecimal quantity) {
        return quantity.setScale(0, RoundingMode.CEILING).intValueExact();
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
