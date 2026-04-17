package com.ppiyaki.prescription.service;

import com.ppiyaki.common.ai.OpenAiClient;
import com.ppiyaki.common.ai.OpenAiClient.ExtractedMedicine;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.ocr.ClovaOcrClient;
import com.ppiyaki.common.ocr.ClovaOcrClient.OcrResult;
import com.ppiyaki.common.ocr.ClovaOcrClient.OcrToken;
import com.ppiyaki.common.storage.NcpStorageProperties;
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
import com.ppiyaki.prescription.controller.dto.PrescriptionDetailResponse;
import com.ppiyaki.prescription.controller.dto.PrescriptionListResponse;
import com.ppiyaki.prescription.repository.PrescriptionMedicineCandidateRepository;
import com.ppiyaki.prescription.repository.PrescriptionRepository;
import com.ppiyaki.user.repository.CareRelationRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionMedicineCandidateRepository candidateRepository;
    private final MedicineRepository medicineRepository;
    private final CareRelationRepository careRelationRepository;
    private final ClovaOcrClient clovaOcrClient;
    private final OpenAiClient openAiClient;
    private final MedicineMatchService medicineMatchService;
    private final PiiMaskingService piiMaskingService;
    private final ImageOrientationCorrector orientationCorrector;
    private final NcpStorageProperties storageProperties;
    private final S3Client s3Client;

    public PrescriptionService(
            final PrescriptionRepository prescriptionRepository,
            final PrescriptionMedicineCandidateRepository candidateRepository,
            final MedicineRepository medicineRepository,
            final CareRelationRepository careRelationRepository,
            final ClovaOcrClient clovaOcrClient,
            final OpenAiClient openAiClient,
            final MedicineMatchService medicineMatchService,
            final PiiMaskingService piiMaskingService,
            final ImageOrientationCorrector orientationCorrector,
            final NcpStorageProperties storageProperties,
            final S3Client s3Client
    ) {
        this.prescriptionRepository = prescriptionRepository;
        this.candidateRepository = candidateRepository;
        this.medicineRepository = medicineRepository;
        this.careRelationRepository = careRelationRepository;
        this.clovaOcrClient = clovaOcrClient;
        this.openAiClient = openAiClient;
        this.medicineMatchService = medicineMatchService;
        this.piiMaskingService = piiMaskingService;
        this.orientationCorrector = orientationCorrector;
        this.storageProperties = storageProperties;
        this.s3Client = s3Client;
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
                final String rawText = namePrefix + med.name()
                        + (med.dosage() != null ? " " + med.dosage() : "");

                candidateRepository.save(new PrescriptionMedicineCandidate(
                        prescription.getId(),
                        rawText,
                        med.name(),
                        med.dosage(),
                        med.schedule(),
                        matched != null ? matched.itemSeq() : null,
                        matched != null ? matched.itemName() : null,
                        matchResult.matchType(),
                        matchResult.reason()
                ));
            }

            prescription.complete(maskedObjectKey);
            deleteOriginalImage(objectKey);

            final List<PrescriptionMedicineCandidate> candidates = candidateRepository.findByPrescriptionId(prescription
                    .getId());
            return PrescriptionDetailResponse.from(prescription, candidates);

        } catch (final Exception e) {
            log.error("Prescription processing failed: prescriptionId={}", prescription.getId(), e);
            prescription.fail("처방전 처리 중 오류 발생");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "처방전 처리 중 오류가 발생했습니다");
        }
    }

    @Transactional(readOnly = true)
    public PrescriptionDetailResponse getDetail(final Long userId, final Long prescriptionId) {
        final Prescription prescription = findPrescription(prescriptionId);
        validateAccess(userId, prescription.getOwnerId());
        final List<PrescriptionMedicineCandidate> candidates = candidateRepository.findByPrescriptionId(prescriptionId);
        return PrescriptionDetailResponse.from(prescription, candidates);
    }

    @Transactional(readOnly = true)
    public PrescriptionListResponse listByOwner(final Long userId, final PrescriptionStatus status) {
        final List<Prescription> prescriptions;
        if (status != null) {
            prescriptions = prescriptionRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(userId, status);
        } else {
            prescriptions = prescriptionRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
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
        validateAccess(userId, prescription.getOwnerId());

        final PrescriptionMedicineCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDICINE_NOT_FOUND));

        switch (request.decision()) {
            case ACCEPTED -> candidate.accept();
            case REJECTED -> candidate.reject();
            case MANUALLY_CORRECTED -> candidate.correctManually(request.chosenItemSeq());
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid decision: " + request.decision());
        }
    }

    @Transactional
    public PrescriptionDetailResponse confirm(final Long userId, final Long prescriptionId) {
        final Prescription prescription = findPrescription(prescriptionId);
        validateAccess(userId, prescription.getOwnerId());

        final List<PrescriptionMedicineCandidate> candidates = candidateRepository.findByPrescriptionId(prescriptionId);

        final boolean allDecided = candidates.stream()
                .noneMatch(c -> c.getCaregiverDecision() == CaregiverDecision.PENDING);
        if (!allDecided) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "All candidates must be decided before confirming.");
        }

        for (final PrescriptionMedicineCandidate candidate : candidates) {
            if (candidate.getCaregiverDecision() == CaregiverDecision.ACCEPTED
                    || candidate.getCaregiverDecision() == CaregiverDecision.MANUALLY_CORRECTED) {

                final String itemSeq = candidate.getCaregiverChosenItemSeq() != null
                        ? candidate.getCaregiverChosenItemSeq()
                        : candidate.getMatchedItemSeq();
                final String name = candidate.getMatchedItemName() != null
                        ? candidate.getMatchedItemName()
                        : candidate.getExtractedName();

                final Medicine medicine = new Medicine(
                        prescription.getOwnerId(), prescription.getId(),
                        name, 0, 0, itemSeq, null);
                medicineRepository.save(medicine);
                candidate.linkMedicine(medicine.getId());
            }
        }

        prescription.confirm();
        return PrescriptionDetailResponse.from(prescription, candidates);
    }

    @Transactional
    public void reject(final Long userId, final Long prescriptionId) {
        final Prescription prescription = findPrescription(prescriptionId);
        validateAccess(userId, prescription.getOwnerId());
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

    private void validateAccess(final Long userId, final Long ownerId) {
        if (userId.equals(ownerId)) {
            return;
        }
        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(userId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
    }
}
