package com.ppiyaki.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ppiyaki.common.ai.OpenAiClient;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.ocr.ClovaOcrClient;
import com.ppiyaki.common.storage.NcpStorageProperties;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.medicine.service.MatchType;
import com.ppiyaki.medicine.service.MedicineMatchService;
import com.ppiyaki.prescription.CaregiverDecision;
import com.ppiyaki.prescription.ImageOrientationCorrector;
import com.ppiyaki.prescription.PiiMaskingService;
import com.ppiyaki.prescription.Prescription;
import com.ppiyaki.prescription.PrescriptionMedicineCandidate;
import com.ppiyaki.prescription.PrescriptionStatus;
import com.ppiyaki.prescription.controller.dto.PrescriptionMedicineAddRequest;
import com.ppiyaki.prescription.repository.PrescriptionMedicineCandidateRepository;
import com.ppiyaki.prescription.repository.PrescriptionRepository;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.repository.CareRelationRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrescriptionService.addManualMedicine")
class PrescriptionServiceManualAddTest {

    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private PrescriptionMedicineCandidateRepository candidateRepository;
    @Mock
    private MedicineRepository medicineRepository;
    @Mock
    private CareRelationRepository careRelationRepository;
    @Mock
    private ClovaOcrClient clovaOcrClient;
    @Mock
    private OpenAiClient openAiClient;
    @Mock
    private MedicineMatchService medicineMatchService;
    @Mock
    private PiiMaskingService piiMaskingService;
    @Mock
    private ImageOrientationCorrector orientationCorrector;
    @Mock
    private NcpStorageProperties storageProperties;
    @Mock
    private S3Client s3Client;

    @InjectMocks
    private PrescriptionService prescriptionService;

    @Test
    @DisplayName("PENDING_REVIEW 상태 처방전에 보호자가 약물을 수동 추가하면 ACCEPTED 후보가 생성된다")
    void 보호자_수동_추가_성공() throws Exception {
        // given
        final Long ownerId = 100L;
        final Long prescriptionId = 1L;
        final Prescription prescription = givenPrescription(prescriptionId, ownerId, PrescriptionStatus.PENDING_REVIEW);
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(candidateRepository.findByPrescriptionId(prescriptionId)).thenReturn(List.of());

        final PrescriptionMedicineAddRequest request = new PrescriptionMedicineAddRequest(
                "199500096", "타이레놀정 500mg", "1정", "1일 3회 식후");

        // when
        prescriptionService.addManualMedicine(ownerId, prescriptionId, request);

        // then
        final ArgumentCaptor<PrescriptionMedicineCandidate> captor = ArgumentCaptor.forClass(
                PrescriptionMedicineCandidate.class);
        verify(candidateRepository).save(captor.capture());
        final PrescriptionMedicineCandidate saved = captor.getValue();
        assertThat(saved.getMatchedItemSeq()).isEqualTo("199500096");
        assertThat(saved.getMatchedItemName()).isEqualTo("타이레놀정 500mg");
        assertThat(saved.getExtractedDosage()).isEqualTo("1정");
        assertThat(saved.getExtractedSchedule()).isEqualTo("1일 3회 식후");
        assertThat(saved.getMatchType()).isEqualTo(MatchType.EXACT);
        assertThat(saved.getCaregiverDecision()).isEqualTo(CaregiverDecision.ACCEPTED);
        assertThat(saved.getCaregiverChosenItemSeq()).isEqualTo("199500096");
        assertThat(saved.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("활성 care_relations 보호자도 수동 추가 가능하다")
    void 보호자_권한_수동_추가() throws Exception {
        // given
        final Long seniorId = 100L;
        final Long caregiverId = 200L;
        final Long prescriptionId = 1L;
        final Prescription prescription = givenPrescription(prescriptionId, seniorId,
                PrescriptionStatus.PENDING_REVIEW);
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(caregiverId, seniorId))
                .thenReturn(Optional.of(new CareRelation(seniorId, caregiverId, "INVITE")));
        when(candidateRepository.findByPrescriptionId(prescriptionId)).thenReturn(List.of());

        final PrescriptionMedicineAddRequest request = new PrescriptionMedicineAddRequest(
                "199500096", "타이레놀정 500mg", null, null);

        // when
        prescriptionService.addManualMedicine(caregiverId, prescriptionId, request);

        // then
        verify(candidateRepository).save(any(PrescriptionMedicineCandidate.class));
    }

    @Test
    @DisplayName("권한 없는 사용자 요청 시 CARE_RELATION_NOT_FOUND 예외")
    void 권한없는_사용자_요청_실패() throws Exception {
        // given
        final Long ownerId = 100L;
        final Long otherUserId = 999L;
        final Long prescriptionId = 1L;
        final Prescription prescription = givenPrescription(prescriptionId, ownerId, PrescriptionStatus.PENDING_REVIEW);
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(otherUserId, ownerId))
                .thenReturn(Optional.empty());

        final PrescriptionMedicineAddRequest request = new PrescriptionMedicineAddRequest(
                "199500096", "타이레놀정 500mg", null, null);

        // when & then
        assertThatThrownBy(() -> prescriptionService.addManualMedicine(otherUserId, prescriptionId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARE_RELATION_NOT_FOUND);
        verify(candidateRepository, never()).save(any());
    }

    @Test
    @DisplayName("PENDING_REVIEW가 아닌 상태에서 추가 시 INVALID_INPUT 예외")
    void 잘못된_상태_요청_실패() throws Exception {
        // given
        final Long ownerId = 100L;
        final Long prescriptionId = 1L;
        final Prescription prescription = givenPrescription(prescriptionId, ownerId, PrescriptionStatus.CONFIRMED);
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));

        final PrescriptionMedicineAddRequest request = new PrescriptionMedicineAddRequest(
                "199500096", "타이레놀정 500mg", null, null);

        // when & then
        assertThatThrownBy(() -> prescriptionService.addManualMedicine(ownerId, prescriptionId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(candidateRepository, never()).save(any());
    }

    @Test
    @DisplayName("처방전 미존재 시 MEDICINE_NOT_FOUND 예외")
    void 처방전_미존재_실패() {
        // given
        when(prescriptionRepository.findById(999L)).thenReturn(Optional.empty());
        final PrescriptionMedicineAddRequest request = new PrescriptionMedicineAddRequest(
                "199500096", "타이레놀정 500mg", null, null);

        // when & then
        assertThatThrownBy(() -> prescriptionService.addManualMedicine(100L, 999L, request))
                .isInstanceOf(BusinessException.class);
        verify(candidateRepository, never()).save(any());
    }

    private Prescription givenPrescription(
            final Long id,
            final Long ownerId,
            final PrescriptionStatus targetStatus
    ) throws Exception {
        final Prescription prescription = new Prescription(ownerId);
        setField(prescription, "id", id);
        setField(prescription, "status", targetStatus);
        return prescription;
    }

    private static void setField(final Object target, final String fieldName, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
