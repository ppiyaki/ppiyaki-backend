package com.ppiyaki.prescription.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ppiyaki.common.ai.OpenAiClient;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.ocr.ClovaOcrClient;
import com.ppiyaki.common.storage.NcpStorageProperties;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.medicine.service.MedicineMatchService;
import com.ppiyaki.prescription.ImageOrientationCorrector;
import com.ppiyaki.prescription.PiiMaskingService;
import com.ppiyaki.prescription.Prescription;
import com.ppiyaki.prescription.PrescriptionStatus;
import com.ppiyaki.prescription.controller.dto.PrescriptionMedicineAddRequest;
import com.ppiyaki.prescription.repository.PrescriptionMedicineCandidateRepository;
import com.ppiyaki.prescription.repository.PrescriptionRepository;
import com.ppiyaki.user.AuthProvider;
import com.ppiyaki.user.CareMode;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.Gender;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrescriptionService 권한 분기 (모드별 + 72h fallback)")
class PrescriptionServicePermissionTest {

    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private PrescriptionMedicineCandidateRepository candidateRepository;
    @Mock
    private MedicineRepository medicineRepository;
    @Mock
    private CareRelationRepository careRelationRepository;
    @Mock
    private UserRepository userRepository;
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

    private static final Long SENIOR_ID = 100L;
    private static final Long CAREGIVER_ID = 200L;
    private static final Long OTHER_ID = 999L;
    private static final Long PRESCRIPTION_ID = 1L;

    @Test
    @DisplayName("[변경] AUTONOMOUS 모드: 시니어 본인은 즉시 통과한다")
    void 자율형_시니어_변경_성공() throws Exception {
        // given
        final Prescription prescription = givenPrescription(LocalDateTime.now());
        when(prescriptionRepository.findById(PRESCRIPTION_ID)).thenReturn(Optional.of(prescription));
        when(userRepository.findById(SENIOR_ID)).thenReturn(Optional.of(givenSenior(CareMode.AUTONOMOUS)));
        when(candidateRepository.findByPrescriptionId(PRESCRIPTION_ID)).thenReturn(List.of());

        // when & then
        assertThatCode(() -> prescriptionService.addManualMedicine(SENIOR_ID, PRESCRIPTION_ID, validRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[변경] AUTONOMOUS 모드: 활성 보호자도 통과한다")
    void 자율형_보호자_변경_성공() throws Exception {
        // given
        final Prescription prescription = givenPrescription(LocalDateTime.now());
        when(prescriptionRepository.findById(PRESCRIPTION_ID)).thenReturn(Optional.of(prescription));
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(CAREGIVER_ID, SENIOR_ID))
                .thenReturn(Optional.of(new CareRelation(SENIOR_ID, CAREGIVER_ID, "INVITE")));
        when(candidateRepository.findByPrescriptionId(PRESCRIPTION_ID)).thenReturn(List.of());

        // when & then
        assertThatCode(() -> prescriptionService.addManualMedicine(CAREGIVER_ID, PRESCRIPTION_ID, validRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[변경] MANAGED 모드 + 72h 미경과: 시니어 본인은 CARE_MODE_RESTRICTED")
    void 관리형_시니어_72h_미경과_차단() throws Exception {
        // given — 1시간 전 생성된 처방전
        final Prescription prescription = givenPrescription(LocalDateTime.now().minusHours(1));
        when(prescriptionRepository.findById(PRESCRIPTION_ID)).thenReturn(Optional.of(prescription));
        when(userRepository.findById(SENIOR_ID)).thenReturn(Optional.of(givenSenior(CareMode.MANAGED)));

        // when & then
        assertThatThrownBy(() -> prescriptionService.addManualMedicine(SENIOR_ID, PRESCRIPTION_ID, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARE_MODE_RESTRICTED);
    }

    @Test
    @DisplayName("[변경] MANAGED 모드 + 72h 경과: 시니어 본인 fallback 통과")
    void 관리형_시니어_72h_경과_fallback() throws Exception {
        // given — 73시간 전 생성된 처방전
        final Prescription prescription = givenPrescription(LocalDateTime.now().minusHours(73));
        when(prescriptionRepository.findById(PRESCRIPTION_ID)).thenReturn(Optional.of(prescription));
        when(userRepository.findById(SENIOR_ID)).thenReturn(Optional.of(givenSenior(CareMode.MANAGED)));
        when(candidateRepository.findByPrescriptionId(PRESCRIPTION_ID)).thenReturn(List.of());

        // when & then
        assertThatCode(() -> prescriptionService.addManualMedicine(SENIOR_ID, PRESCRIPTION_ID, validRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[변경] MANAGED 모드: 활성 보호자는 모드 무관 통과 (72h 미경과 처방전)")
    void 관리형_보호자_72h_미경과_통과() throws Exception {
        // given
        final Prescription prescription = givenPrescription(LocalDateTime.now());
        when(prescriptionRepository.findById(PRESCRIPTION_ID)).thenReturn(Optional.of(prescription));
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(CAREGIVER_ID, SENIOR_ID))
                .thenReturn(Optional.of(new CareRelation(SENIOR_ID, CAREGIVER_ID, "INVITE")));
        when(candidateRepository.findByPrescriptionId(PRESCRIPTION_ID)).thenReturn(List.of());

        // when & then
        assertThatCode(() -> prescriptionService.addManualMedicine(CAREGIVER_ID, PRESCRIPTION_ID, validRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[변경] 관계 없는 사용자: CARE_RELATION_NOT_FOUND")
    void 관계없는_사용자_차단() throws Exception {
        // given
        final Prescription prescription = givenPrescription(LocalDateTime.now());
        when(prescriptionRepository.findById(PRESCRIPTION_ID)).thenReturn(Optional.of(prescription));
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(OTHER_ID, SENIOR_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> prescriptionService.addManualMedicine(OTHER_ID, PRESCRIPTION_ID, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARE_RELATION_NOT_FOUND);
    }

    @Test
    @DisplayName("[조회] getDetail은 모드 무관 — MANAGED + 시니어 + 0h 처방전도 통과")
    void 조회_모드_무관_통과() throws Exception {
        // given — MANAGED 모드 + 방금 생성된 처방전 (변경 시점이라면 차단됨)
        final Prescription prescription = givenPrescription(LocalDateTime.now());
        when(prescriptionRepository.findById(PRESCRIPTION_ID)).thenReturn(Optional.of(prescription));
        when(candidateRepository.findByPrescriptionId(PRESCRIPTION_ID)).thenReturn(List.of());

        // when & then — userRepository 호출 없이 바로 통과 (read access는 시니어 본인 자동 통과)
        assertThatCode(() -> prescriptionService.getDetail(SENIOR_ID, PRESCRIPTION_ID))
                .doesNotThrowAnyException();
    }

    private PrescriptionMedicineAddRequest validRequest() {
        return new PrescriptionMedicineAddRequest("199500096", "타이레놀정 500mg", null, null);
    }

    private Prescription givenPrescription(final LocalDateTime createdAt) throws Exception {
        final Prescription prescription = new Prescription(SENIOR_ID);
        setField(prescription, "id", PRESCRIPTION_ID);
        setField(prescription, "status", PrescriptionStatus.PENDING_REVIEW);
        setField(prescription, "createdAt", createdAt);
        return prescription;
    }

    private User givenSenior(final CareMode careMode) throws Exception {
        final User user = new User(
                "senior", "password", UserRole.SENIOR, AuthProvider.LOCAL, "시니어",
                Gender.UNKNOWN, LocalDate.of(1950, 1, 1), null);
        setField(user, "id", SENIOR_ID);
        user.changeCareMode(careMode);
        return user;
    }

    private static void setField(final Object target, final String fieldName, final Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                final Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (final NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
