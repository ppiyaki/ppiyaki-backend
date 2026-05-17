package com.ppiyaki.prescription.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.infrastructure.ai.OpenAiClient;
import com.ppiyaki.infrastructure.ai.OpenAiClient.ExtractedMedicine;
import com.ppiyaki.infrastructure.ocr.ClovaOcrClient;
import com.ppiyaki.infrastructure.ocr.ClovaOcrClient.OcrResult;
import com.ppiyaki.infrastructure.ocr.ClovaOcrClient.OcrToken;
import com.ppiyaki.infrastructure.storage.NcpStorageProperties;
import com.ppiyaki.infrastructure.storage.PhotoUrlAssembler;
import com.ppiyaki.medication.domain.DosageUnit;
import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.controller.dto.MedicineCandidate;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.medicine.service.MatchResult;
import com.ppiyaki.medicine.service.MedicineMatchService;
import com.ppiyaki.prescription.CaregiverDecision;
import com.ppiyaki.prescription.ImageOrientationCorrector;
import com.ppiyaki.prescription.PiiMaskingService;
import com.ppiyaki.prescription.Prescription;
import com.ppiyaki.prescription.PrescriptionMedicineCandidate;
import com.ppiyaki.prescription.PrescriptionStatus;
import com.ppiyaki.prescription.controller.dto.CandidateDecisionRequest;
import com.ppiyaki.prescription.controller.dto.PrescriptionConfirmRequest;
import com.ppiyaki.prescription.controller.dto.PrescriptionConfirmRequest.MedicineAmountInput;
import com.ppiyaki.prescription.controller.dto.PrescriptionDetailResponse;
import com.ppiyaki.prescription.controller.dto.PrescriptionListResponse;
import com.ppiyaki.prescription.controller.dto.PrescriptionMedicineAddRequest;
import com.ppiyaki.prescription.repository.PrescriptionMedicineCandidateRepository;
import com.ppiyaki.prescription.repository.PrescriptionRepository;
import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@ConditionalOnProperty(prefix = "clova.ocr", name = "secret")
public class PrescriptionService {

    private static final Logger log = LoggerFactory.getLogger(PrescriptionService.class);

    private static final Duration MANAGED_FALLBACK_AFTER = Duration.ofHours(72);

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionMedicineCandidateRepository candidateRepository;
    private final MedicineRepository medicineRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final CareRelationRepository careRelationRepository;
    private final UserRepository userRepository;
    private final ClovaOcrClient clovaOcrClient;
    private final OpenAiClient openAiClient;
    private final MedicineMatchService medicineMatchService;
    private final PiiMaskingService piiMaskingService;
    private final ImageOrientationCorrector orientationCorrector;
    private final NcpStorageProperties storageProperties;
    private final S3Client s3Client;
    private final PhotoUrlAssembler photoUrlAssembler;

    public PrescriptionService(
            final PrescriptionRepository prescriptionRepository,
            final PrescriptionMedicineCandidateRepository candidateRepository,
            final MedicineRepository medicineRepository,
            final MedicationScheduleRepository medicationScheduleRepository,
            final CareRelationRepository careRelationRepository,
            final UserRepository userRepository,
            final ClovaOcrClient clovaOcrClient,
            final OpenAiClient openAiClient,
            final MedicineMatchService medicineMatchService,
            final PiiMaskingService piiMaskingService,
            final ImageOrientationCorrector orientationCorrector,
            final NcpStorageProperties storageProperties,
            final S3Client s3Client,
            final PhotoUrlAssembler photoUrlAssembler
    ) {
        this.prescriptionRepository = prescriptionRepository;
        this.candidateRepository = candidateRepository;
        this.medicineRepository = medicineRepository;
        this.medicationScheduleRepository = medicationScheduleRepository;
        this.careRelationRepository = careRelationRepository;
        this.userRepository = userRepository;
        this.clovaOcrClient = clovaOcrClient;
        this.openAiClient = openAiClient;
        this.medicineMatchService = medicineMatchService;
        this.piiMaskingService = piiMaskingService;
        this.orientationCorrector = orientationCorrector;
        this.storageProperties = storageProperties;
        this.s3Client = s3Client;
        this.photoUrlAssembler = photoUrlAssembler;
    }

    @Transactional
    public PrescriptionDetailResponse processAndCreate(final Long userId, final String objectKey) {
        final Prescription prescription = new Prescription(userId);
        prescriptionRepository.save(prescription);

        try {
            final byte[] rawImageBytes = fetchImage(objectKey);
            final String format = extractFormat(objectKey);
            final byte[] imageBytes = orientationCorrector.correctOrientation(rawImageBytes, format);

            final OcrResult ocrResult = clovaOcrClient.ocr(imageBytes, format);
            log.info("OCR completed: prescriptionId={} tokens={}", prescription.getId(), ocrResult.tokens().size());

            final List<OcrToken> piiTokens = piiMaskingService.identifyPiiTokens(ocrResult.tokens());
            final String maskedText = piiMaskingService.maskText(ocrResult.fullText(), piiTokens);

            final String maskedObjectKey = saveMaskedImage(imageBytes, format, piiTokens, userId);

            final List<ExtractedMedicine> extracted = openAiClient.extractMedicines(maskedText);
            log.info("AI extraction: prescriptionId={} medicines={}", prescription.getId(), extracted.size());

            for (final ExtractedMedicine med : extracted) {
                if (med.name() == null || med.name().isBlank()) {
                    continue;
                }

                final MatchResult matchResult = medicineMatchService.match(
                        med.name(), Optional.ofNullable(med.ingredientName()));

                final MedicineCandidate matched = matchResult.recommended().orElse(null);

                final String mfr = med.manufacturer() != null ? med.manufacturer() : "";
                final String namePrefix = mfr.isEmpty() || med.name().startsWith(mfr) ? "" : mfr;
                final String dosageDisplay = formatDosageDisplay(med.dosageQuantity(), med.dosageUnit());
                final String rawText = namePrefix + med.name()
                        + (dosageDisplay != null ? " " + dosageDisplay : "");

                candidateRepository.save(new PrescriptionMedicineCandidate(
                        prescription.getId(),
                        rawText,
                        med.name(),
                        med.dosageQuantity(),
                        med.dosageUnit(),
                        med.schedule(),
                        matched != null ? matched.itemSeq() : null,
                        matched != null ? matched.itemName() : null,
                        matchResult.matchType(),
                        matchResult.reason(),
                        med.mealSlots()
                ));
                // (note) med.dosageUnit()은 OpenAiClient에서 이미 DosageUnit으로 정규화됨
            }

            prescription.complete(maskedObjectKey);
            deleteOriginalImage(objectKey);

            final List<PrescriptionMedicineCandidate> candidates = candidateRepository.findByPrescriptionId(prescription
                    .getId());
            return PrescriptionDetailResponse.from(prescription, candidates, photoUrlAssembler.toFullUrl(prescription
                    .getMaskedImageObjectKey()));

        } catch (final Exception e) {
            log.error("Prescription processing failed: prescriptionId={}", prescription.getId(), e);
            prescription.fail("처방전 처리 중 오류 발생");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "처방전 처리 중 오류가 발생했습니다");
        }
    }

    @Transactional(readOnly = true)
    public PrescriptionDetailResponse getDetail(final Long userId, final Long prescriptionId) {
        final Prescription prescription = findPrescription(prescriptionId);
        validateReadAccess(userId, prescription);
        final List<PrescriptionMedicineCandidate> candidates = candidateRepository.findByPrescriptionId(prescriptionId);
        return PrescriptionDetailResponse.from(prescription, candidates, photoUrlAssembler.toFullUrl(prescription
                .getMaskedImageObjectKey()));
    }

    @Transactional(readOnly = true)
    public PrescriptionListResponse listByOwner(
            final Long userId,
            final Long seniorIdParam,
            final PrescriptionStatus status
    ) {
        final Long ownerId = seniorIdParam != null ? seniorIdParam : userId;
        if (!userId.equals(ownerId)) {
            careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(userId, ownerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
        }

        final List<Prescription> prescriptions;
        if (status != null) {
            prescriptions = prescriptionRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, status);
        } else {
            prescriptions = prescriptionRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        }
        return PrescriptionListResponse.from(prescriptions);
    }

    @Transactional
    public void updateCandidateDecision(
            final Long userId,
            final Long prescriptionId,
            final Long candidateId,
            final CandidateDecisionRequest request
    ) {
        final Prescription prescription = findPrescription(prescriptionId);
        validateMutationAccess(userId, prescription);

        final PrescriptionMedicineCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDICINE_NOT_FOUND));

        switch (request.decision()) {
            case ACCEPTED -> candidate.accept();
            case REJECTED -> candidate.reject();
            case MANUALLY_CORRECTED -> candidate.correctManually(request.chosenItemSeq());
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid decision: " + request.decision());
        }

        if (request.confirmedMealSlots() != null) {
            candidate.updateConfirmedMealSlots(request.confirmedMealSlots());
        }

        final DosageUnit normalizedUnit = DosageUnit.fromInput(request.dosageUnit()).orElse(null);
        if (request.dosageQuantity() != null || normalizedUnit != null) {
            candidate.updateExtractedDosage(request.dosageQuantity(), normalizedUnit);
        }
    }

    @Transactional
    public PrescriptionDetailResponse addManualMedicine(
            final Long userId,
            final Long prescriptionId,
            final PrescriptionMedicineAddRequest request
    ) {
        final Prescription prescription = findPrescription(prescriptionId);
        validateMutationAccess(userId, prescription);

        if (prescription.getStatus() != PrescriptionStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "Prescription must be in PENDING_REVIEW to add medicines.");
        }

        candidateRepository.save(PrescriptionMedicineCandidate.manualAdd(
                prescription.getId(),
                request.itemSeq(),
                request.itemName(),
                request.dosageQuantity(),
                DosageUnit.fromInput(request.dosageUnit()).orElse(null),
                request.schedule()
        ));

        final List<PrescriptionMedicineCandidate> candidates = candidateRepository.findByPrescriptionId(prescriptionId);
        return PrescriptionDetailResponse.from(prescription, candidates, photoUrlAssembler.toFullUrl(prescription
                .getMaskedImageObjectKey()));
    }

    @Transactional
    public PrescriptionDetailResponse confirm(
            final Long userId,
            final Long prescriptionId,
            final PrescriptionConfirmRequest request
    ) {
        final Prescription prescription = findPrescription(prescriptionId);
        validateMutationAccess(userId, prescription);

        final List<PrescriptionMedicineCandidate> candidates = candidateRepository.findByPrescriptionId(prescriptionId);

        final boolean allDecided = candidates.stream()
                .noneMatch(c -> c.getCaregiverDecision() == CaregiverDecision.PENDING);
        if (!allDecided) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "All candidates must be decided before confirming.");
        }

        final java.util.Map<Long, MedicineAmountInput> amountByCandidate = (request == null
                || request.medicineAmounts() == null)
                        ? java.util.Collections.emptyMap()
                        : request.medicineAmounts().stream()
                                .collect(java.util.stream.Collectors.toMap(MedicineAmountInput::candidateId, m -> m));

        // 시니어 mealTimes 사전 검증: 슬롯 자동 생성 대상 candidate가 있는데
        // 해당 슬롯의 mealTime이 null이면 트랜잭션 변경 시작 전에 거절 (spec §3, USER_002).
        final User owner = userRepository.findById(prescription.getOwnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        for (final PrescriptionMedicineCandidate candidate : candidates) {
            if (!isAcceptedOrCorrected(candidate) || candidate.getCreatedMedicineId() != null) {
                continue;
            }
            for (final MealSlot slot : candidate.getConfirmedMealSlotsList()) {
                if (slot.resolveTime(owner) == null) {
                    throw new BusinessException(ErrorCode.MEAL_TIMES_NOT_SET);
                }
            }
        }

        final LocalDate today = LocalDate.now();
        for (final PrescriptionMedicineCandidate candidate : candidates) {
            if (!isAcceptedOrCorrected(candidate)) {
                continue;
            }
            // 멱등: 이미 Medicine이 생성된 candidate는 skip (재confirm 시 중복 생성 방지).
            if (candidate.getCreatedMedicineId() != null) {
                continue;
            }

            final String itemSeq = candidate.getCaregiverChosenItemSeq() != null
                    ? candidate.getCaregiverChosenItemSeq()
                    : candidate.getMatchedItemSeq();
            final String name = candidate.getMatchedItemName() != null
                    ? candidate.getMatchedItemName()
                    : candidate.getExtractedName();

            final MedicineAmountInput amount = amountByCandidate.get(candidate.getId());
            final int totalAmount = amount != null ? amount.totalAmount() : 0;
            final int remainingAmount = amount != null ? amount.remainingAmount() : 0;
            final Medicine medicine = new Medicine(
                    prescription.getOwnerId(), prescription.getId(),
                    name, totalAmount, remainingAmount, itemSeq, null);
            medicineRepository.save(medicine);
            candidate.linkMedicine(medicine.getId());

            // dosage_quantity가 비어있으면 schedule 자동 생성 skip — Medicine만 등록.
            // 보호자가 후속으로 schedule CRUD API로 보완 (spec §3 Q2).
            final BigDecimal dosageQuantity = candidate.getExtractedDosageQuantity();
            final DosageUnit dosageUnit = candidate.getExtractedDosageUnit();
            if (dosageQuantity == null) {
                continue;
            }
            for (final MealSlot slot : candidate.getConfirmedMealSlotsList()) {
                medicationScheduleRepository.save(new MedicationSchedule(
                        medicine.getId(), slot, dosageQuantity, dosageUnit, "DAILY", today, null));
            }
        }

        prescription.confirm();
        return PrescriptionDetailResponse.from(prescription, candidates, photoUrlAssembler.toFullUrl(prescription
                .getMaskedImageObjectKey()));
    }

    private boolean isAcceptedOrCorrected(final PrescriptionMedicineCandidate candidate) {
        return candidate.getCaregiverDecision() == CaregiverDecision.ACCEPTED
                || candidate.getCaregiverDecision() == CaregiverDecision.MANUALLY_CORRECTED;
    }

    /**
     * candidate raw text 표시용 dosage 합성. 양쪽 다 null이면 null.
     */
    private static String formatDosageDisplay(final BigDecimal quantity, final DosageUnit unit) {
        if (quantity == null && unit == null) {
            return null;
        }
        final String quantityText = quantity != null ? quantity.stripTrailingZeros().toPlainString() : "";
        final String unitText = unit != null ? unit.getDisplayValue() : "";
        return quantityText + unitText;
    }

    @Transactional
    public void reject(final Long userId, final Long prescriptionId) {
        final Prescription prescription = findPrescription(prescriptionId);
        validateMutationAccess(userId, prescription);
        prescription.reject();
    }

    private byte[] fetchImage(final String objectKey) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(storageProperties.bucketName())
                    .key(objectKey)
                    .build()).asByteArray();
        } catch (final Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Failed to fetch image: " + e.getMessage());
        }
    }

    private String saveMaskedImage(
            final byte[] originalBytes,
            final String format,
            final List<OcrToken> piiTokens,
            final Long userId
    ) {
        try {
            final BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalBytes));
            final BufferedImage masked = piiMaskingService.maskImage(original, piiTokens);

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(masked, format.equalsIgnoreCase("png") ? "png" : "jpg", baos);

            final String maskedKey = "masked/prescription/" + userId + "/" + UUID.randomUUID() + "." + format;

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(storageProperties.bucketName())
                            .key(maskedKey)
                            .contentType("image/" + (format.equalsIgnoreCase("png") ? "png" : "jpeg"))
                            .build(),
                    RequestBody.fromBytes(baos.toByteArray()));

            return maskedKey;
        } catch (final Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Failed to save masked image: " + e.getMessage());
        }
    }

    private void deleteOriginalImage(final String objectKey) {
        try {
            s3Client.deleteObject(builder -> builder.bucket(storageProperties.bucketName()).key(objectKey));
        } catch (final Exception e) {
            log.warn("Failed to delete original image: key={} error={}", objectKey, e.getMessage());
        }
    }

    private String extractFormat(final String objectKey) {
        final int dot = objectKey.lastIndexOf('.');
        return dot >= 0 ? objectKey.substring(dot + 1) : "jpg";
    }

    private Prescription findPrescription(final Long prescriptionId) {
        return prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDICINE_NOT_FOUND));
    }

    /**
     * 처방전 조회 권한 검증 (모드 무관). 시니어 본인 또는 활성 보호자만 통과.
     */
    private void validateReadAccess(final Long userId, final Prescription prescription) {
        final Long ownerId = prescription.getOwnerId();
        if (userId.equals(ownerId)) {
            return;
        }
        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(userId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
    }

    /**
     * 처방전 변경 권한 검증 (모드별 분기, spec §5-2-1).
     * - AUTONOMOUS 시니어/보호자: 즉시 통과
     * - MANAGED 시니어 0~72h: CARE_MODE_RESTRICTED
     * - MANAGED 시니어 72h+: fallback 통과
     * - 활성 보호자: 모드 무관 통과
     */
    private void validateMutationAccess(final Long userId, final Prescription prescription) {
        final Long ownerId = prescription.getOwnerId();
        if (userId.equals(ownerId)) {
            final User senior = userRepository.findById(ownerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            if (senior.getCareMode() == CareMode.AUTONOMOUS) {
                return;
            }
            final Duration elapsed = Duration.between(prescription.getCreatedAt(), LocalDateTime.now());
            if (elapsed.compareTo(MANAGED_FALLBACK_AFTER) >= 0) {
                return;
            }
            throw new BusinessException(ErrorCode.CARE_MODE_RESTRICTED);
        }
        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(userId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
    }
}
