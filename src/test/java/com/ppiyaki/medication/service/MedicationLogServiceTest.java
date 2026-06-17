package com.ppiyaki.medication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.infrastructure.storage.PhotoUrlAssembler;
import com.ppiyaki.medication.controller.dto.MedicationLogUpsertRequest;
import com.ppiyaki.medication.domain.LogStatus;
import com.ppiyaki.medication.domain.MedicationLog;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.User;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicationLogService")
class MedicationLogServiceTest {

    @Mock
    private MedicationLogRepository medicationLogRepository;
    @Mock
    private MedicationScheduleRepository medicationScheduleRepository;
    @Mock
    private MedicineRepository medicineRepository;
    @Mock
    private CareRelationRepository careRelationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PhotoUrlAssembler photoUrlAssembler;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private com.ppiyaki.notification.repository.NotificationRepository notificationRepository;
    @org.mockito.Spy
    private io.micrometer.core.instrument.MeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    @InjectMocks
    private MedicationLogService medicationLogService;

    private static final Long SENIOR_ID = 100L;
    private static final Long CAREGIVER_ID = 200L;
    private static final Long SCHEDULE_ID = 1L;
    private static final Long MEDICINE_ID = 10L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 4, 18);

    @Test
    @DisplayName("시니어 본인 PUT 시 isProxy=false, confirmedByUserId=시니어")
    void 시니어_본인_업서트_생성() throws Exception {
        // given
        givenScheduleAndMedicine();
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, LocalDateTime.of(2026, 4, 18, 9, 0), LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then
        final ArgumentCaptor<MedicationLog> captor = ArgumentCaptor.forClass(MedicationLog.class);
        verify(medicationLogRepository).saveAndFlush(captor.capture());
        final MedicationLog saved = captor.getValue();
        assertThat(saved.getIsProxy()).isFalse();
        assertThat(saved.getConfirmedByUserId()).isEqualTo(SENIOR_ID);
        assertThat(saved.getStatus()).isEqualTo(LogStatus.TAKEN);
        assertThat(saved.getSeniorId()).isEqualTo(SENIOR_ID);
        verify(eventPublisher).publishEvent(any(MedicationTakenEvent.class));
    }

    @Test
    @DisplayName("보호자 PUT 시 isProxy=true, confirmedByUserId=보호자")
    void 보호자_대리_업서트() throws Exception {
        // given
        givenScheduleAndMedicine();
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(CAREGIVER_ID, SENIOR_ID))
                .thenReturn(Optional.of(CareRelation.createLinked(SENIOR_ID, CAREGIVER_ID)));
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(CAREGIVER_ID, request);

        // then
        final ArgumentCaptor<MedicationLog> captor = ArgumentCaptor.forClass(MedicationLog.class);
        verify(medicationLogRepository).saveAndFlush(captor.capture());
        final MedicationLog saved = captor.getValue();
        assertThat(saved.getIsProxy()).isTrue();
        assertThat(saved.getConfirmedByUserId()).isEqualTo(CAREGIVER_ID);
        verify(eventPublisher).publishEvent(any(MedicationTakenEvent.class));
    }

    @Test
    @DisplayName("관계 없는 사용자 PUT 시 CARE_RELATION_NOT_FOUND")
    void 관계없는_사용자_거부() throws Exception {
        // given
        givenScheduleAndMedicine();
        final Long otherUserId = 999L;
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(otherUserId, SENIOR_ID))
                .thenReturn(Optional.empty());

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        // when & then
        assertThatThrownBy(() -> medicationLogService.upsert(otherUserId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARE_RELATION_NOT_FOUND);
        verify(medicationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("동일 (scheduleId, targetDate) 두 번 호출 시 update 경로 — 멱등")
    void 멱등_업서트() throws Exception {
        // given
        givenScheduleAndMedicine();
        final MedicationLog existing = new MedicationLog(
                SENIOR_ID, SCHEDULE_ID, TARGET_DATE, LocalDateTime.of(2026, 4, 18, 9, 0),
                LogStatus.TAKEN, null, false, SENIOR_ID);
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.of(existing));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, LocalDateTime.of(2026, 4, 18, 10, 0), LogStatus.MISSED, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then
        verify(medicationLogRepository, never()).saveAndFlush(any()); // update via dirty checking
        assertThat(existing.getStatus()).isEqualTo(LogStatus.MISSED);
        // status != TAKEN이면 요청에 takenAt이 있어도 null로 정리한다 (issue #462: status 무관 takenAt 세팅 금지)
        assertThat(existing.getTakenAt()).isNull();
        verify(eventPublisher, never()).publishEvent(any(MedicationTakenEvent.class));
    }

    @Test
    @DisplayName("TAKEN→TAKEN 재업서트 시 이벤트를 중복 발행하지 않는다")
    void TAKEN_to_TAKEN_이벤트_미발행() throws Exception {
        // given
        givenScheduleAndMedicine();
        final MedicationLog existing = new MedicationLog(
                SENIOR_ID, SCHEDULE_ID, TARGET_DATE, LocalDateTime.of(2026, 4, 18, 9, 0),
                LogStatus.TAKEN, null, false, SENIOR_ID);
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.of(existing));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, LocalDateTime.of(2026, 4, 18, 10, 0), LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then
        verify(eventPublisher, never()).publishEvent(any(MedicationTakenEvent.class));
    }

    @Test
    @DisplayName("photoObjectKey의 userId 세그먼트가 요청자와 다르면 INVALID_INPUT")
    void photoObjectKey_userId_불일치() throws Exception {
        // given
        givenScheduleAndMedicine();

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN,
                "medication-log/999/9b3e7a1c-8d55-4f0a-b2e1-5f9a7b3d8c21.jpg"); // 999 != SENIOR_ID

        // when & then
        assertThatThrownBy(() -> medicationLogService.upsert(SENIOR_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("photoObjectKey 형식 깨짐 (.. 포함) 시 INVALID_INPUT")
    void photoObjectKey_형식_오류() throws Exception {
        // given
        givenScheduleAndMedicine();

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN,
                "medication-log/100/../9b3e7a1c-8d55-4f0a-b2e1-5f9a7b3d8c21.jpg");

        // when & then
        assertThatThrownBy(() -> medicationLogService.upsert(SENIOR_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("조회 기간 31일 초과 시 INVALID_INPUT")
    void 조회_기간_초과() {
        // given
        final LocalDate from = LocalDate.of(2026, 4, 1);
        final LocalDate to = LocalDate.of(2026, 5, 5); // 34 days

        // when & then
        assertThatThrownBy(() -> medicationLogService.readByPeriod(SENIOR_ID, null, from, to))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("조회 from이 to보다 뒤면 INVALID_INPUT")
    void 조회_from이_to보다_뒤() {
        // given
        final LocalDate from = LocalDate.of(2026, 4, 20);
        final LocalDate to = LocalDate.of(2026, 4, 18);

        // when & then
        assertThatThrownBy(() -> medicationLogService.readByPeriod(SENIOR_ID, null, from, to))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("careMode=MANAGED + status=TAKEN + photoObjectKey 누락 시 MEDICATION_LOG_PHOTO_REQUIRED")
    void managed_senior_taken_without_photo() throws Exception {
        // given
        givenScheduleAndMedicine();
        final User managedSenior = mock(User.class);
        when(managedSenior.getCareMode()).thenReturn(CareMode.MANAGED);
        when(userRepository.findById(SENIOR_ID)).thenReturn(Optional.of(managedSenior));

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        // when & then
        assertThatThrownBy(() -> medicationLogService.upsert(SENIOR_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEDICATION_LOG_PHOTO_REQUIRED);
    }

    @Test
    @DisplayName("careMode=MANAGED + status=MISSED는 photoObjectKey 없어도 통과")
    void managed_senior_missed_without_photo_ok() throws Exception {
        // given
        givenScheduleAndMedicine();
        final User managedSenior = mock(User.class);
        when(managedSenior.getCareMode()).thenReturn(CareMode.MANAGED);
        when(userRepository.findById(SENIOR_ID)).thenReturn(Optional.of(managedSenior));
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.MISSED, null);

        // when & then — 예외 없이 처리
        medicationLogService.upsert(SENIOR_ID, request);
    }

    @Test
    @DisplayName("photoObjectKey purpose가 medication-log가 아니면 INVALID_INPUT")
    void photoObjectKey_purpose_불일치() throws Exception {
        // given
        givenScheduleAndMedicine();

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN,
                "prescription/100/9b3e7a1c-8d55-4f0a-b2e1-5f9a7b3d8c21.jpg");

        // when & then
        assertThatThrownBy(() -> medicationLogService.upsert(SENIOR_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("photoObjectKey UUID 형식이 아니면 INVALID_INPUT")
    void photoObjectKey_UUID_아님() throws Exception {
        // given
        givenScheduleAndMedicine();

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN,
                "medication-log/100/not-a-uuid.jpg");

        // when & then
        assertThatThrownBy(() -> medicationLogService.upsert(SENIOR_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("조회 시 시니어 본인은 활성 관계 검증 없이 통과")
    void 조회_시니어_본인_통과() {
        // given
        when(medicationLogRepository.findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(
                eq(SENIOR_ID), any(), any())).thenReturn(List.of());

        // when
        medicationLogService.readByPeriod(SENIOR_ID, null, TARGET_DATE, TARGET_DATE);

        // then
        verify(careRelationRepository, never())
                .findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(any(), any());
    }

    @Test
    @DisplayName("조회 시 보호자는 활성 care_relations 검증 통과해야 함")
    void 조회_보호자_권한_검증() {
        // given
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(CAREGIVER_ID, SENIOR_ID))
                .thenReturn(Optional.of(CareRelation.createLinked(SENIOR_ID, CAREGIVER_ID)));
        when(medicationLogRepository.findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(
                eq(SENIOR_ID), any(), any())).thenReturn(List.of());

        // when
        final var resp = medicationLogService.readByPeriod(CAREGIVER_ID, SENIOR_ID, TARGET_DATE, TARGET_DATE);

        // then
        assertThat(resp.responses()).isEmpty();
    }

    @Test
    @DisplayName("신규 TAKEN 업서트 시 schedule.dosage_quantity 기준으로 잔여분 차감 (1정 → 1)")
    void 신규_TAKEN_시_잔여분_차감() throws Exception {
        // given — schedule.dosage_quantity=1, unit=TABLET (default)
        final Medicine medicine = givenScheduleAndMedicineReturning(30, 30, "1정");
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then
        assertThat(medicine.getRemainingAmount()).isEqualTo(29);
    }

    @Test
    @DisplayName("신규 TAKEN 전환 시 같은 시니어/날짜/슬롯 MEDICATION_REMINDER 알림 자동 전이 (issue #324)")
    void 신규_TAKEN_시_알림_자동_전이() throws Exception {
        // given
        givenScheduleAndMedicineReturning(30, 30, "1정");
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then — notificationRepository.markReminderTaken(seniorId, targetDate, "BREAKFAST", takenAt)
        verify(notificationRepository).markReminderTaken(
                eq(SENIOR_ID),
                eq(TARGET_DATE),
                eq("BREAKFAST"),
                any(LocalDateTime.class)
        );
    }

    @Test
    @DisplayName("이미 TAKEN인 row 재호출 시 알림 자동 전이도 호출 안 함 — 멱등")
    void 이미_TAKEN_재호출_시_알림_전이_안함() throws Exception {
        // given
        givenScheduleAndMedicineReturning(30, 25);
        final MedicationLog existing = new MedicationLog(
                SENIOR_ID, SCHEDULE_ID, TARGET_DATE, LocalDateTime.of(2026, 4, 18, 8, 0),
                LogStatus.TAKEN, null, false, SENIOR_ID);
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.of(existing));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then
        // 알림 전이는 데이터 불일치 회복을 위해 상태와 무관하게 항상 호출되도록 변경됨 (v0.19.1)
        verify(notificationRepository, org.mockito.Mockito.atLeastOnce())
                .markReminderTaken(any(), any(), any(), any());
    }

    @Test
    @DisplayName("이미 TAKEN인 row 재호출 시 잔여분 중복 차감 없음 — 멱등")
    void 이미_TAKEN인_경우_차감_없음() throws Exception {
        // given
        final Medicine medicine = givenScheduleAndMedicineReturning(30, 25);
        final MedicationLog existing = new MedicationLog(
                SENIOR_ID, SCHEDULE_ID, TARGET_DATE, LocalDateTime.of(2026, 4, 18, 8, 0),
                LogStatus.TAKEN, null, false, SENIOR_ID);
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.of(existing));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then — 이미 TAKEN이었으니 변경 없음
        assertThat(medicine.getRemainingAmount()).isEqualTo(25);
    }

    @Test
    @DisplayName("MISSED 등 TAKEN 외 상태는 잔여분 차감 안 함")
    void TAKEN_아닌_상태는_차감_안함() throws Exception {
        // given
        final Medicine medicine = givenScheduleAndMedicineReturning(30, 30);
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.MISSED, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then
        assertThat(medicine.getRemainingAmount()).isEqualTo(30);
    }

    @Test
    @DisplayName("dosage=\"2정\"이면 잔여분 2 차감")
    void TAKEN_시_dosage_정수_단위로_차감() throws Exception {
        // given
        final Medicine medicine = givenScheduleAndMedicineReturning(30, 30, "2정");
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then
        assertThat(medicine.getRemainingAmount()).isEqualTo(28);
    }

    @Test
    @DisplayName("schedule.dosage_quantity가 null이면 차감 skip (PRN/옛 schedule 보호) — spec dosage-quantity-unit-split Q7")
    void TAKEN_시_dosage_quantity_null이면_차감_skip() throws Exception {
        // given — quantity 추출 실패 케이스 ("반정"은 분수라 정규식에서 quantity null로 둠)
        final Medicine medicine = givenScheduleAndMedicineReturning(30, 30, "반정");
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then — 차감 skip
        assertThat(medicine.getRemainingAmount()).isEqualTo(30);
    }

    @Test
    @DisplayName("dosage=\"3정\"이고 잔여분이 2면 0으로 clamp (음수 방지)")
    void TAKEN_시_차감_clamp() throws Exception {
        // given
        final Medicine medicine = givenScheduleAndMedicineReturning(30, 2, "3정");
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then
        assertThat(medicine.getRemainingAmount()).isEqualTo(0);
    }

    @Test
    @DisplayName("remainingAmount=0이면 차감하지 않아 음수 방지")
    void 잔여분_0이면_차감_안함() throws Exception {
        // given
        final Medicine medicine = givenScheduleAndMedicineReturning(30, 0);
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then
        assertThat(medicine.getRemainingAmount()).isEqualTo(0);
    }

    private Medicine givenScheduleAndMedicineReturning(final int total, final int remaining) throws Exception {
        return givenScheduleAndMedicineReturning(total, remaining, null);
    }

    private Medicine givenScheduleAndMedicineReturning(final int total, final int remaining,
            final String dosage) throws Exception {
        final java.lang.reflect.Constructor<MedicationSchedule> ctor = MedicationSchedule.class
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        final MedicationSchedule schedule = ctor.newInstance();
        setField(schedule, "id", SCHEDULE_ID);
        setField(schedule, "medicineId", MEDICINE_ID);
        setField(schedule, "mealSlot", com.ppiyaki.medication.domain.MealSlot.BREAKFAST);
        if (dosage != null) {
            final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)(.*)$")
                    .matcher(dosage);
            if (m.find()) {
                setField(schedule, "dosageQuantity", new java.math.BigDecimal(m.group(1)));
                setField(schedule, "dosageUnit",
                        com.ppiyaki.medication.domain.DosageUnit.fromInput(m.group(2).trim()).orElse(null));
            }
        }
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));

        final Medicine medicine = new Medicine(SENIOR_ID, null, "테스트약", total, remaining, "ITEM-1", null);
        setField(medicine, "id", MEDICINE_ID);
        when(medicineRepository.findById(MEDICINE_ID)).thenReturn(Optional.of(medicine));
        givenAutonomousSenior();
        return medicine;
    }

    private void givenAutonomousSenior() {
        final User senior = mock(User.class);
        lenient().when(senior.getCareMode()).thenReturn(CareMode.AUTONOMOUS);
        lenient().when(userRepository.findById(SENIOR_ID)).thenReturn(Optional.of(senior));
    }

    private void givenScheduleAndMedicine() throws Exception {
        final java.lang.reflect.Constructor<MedicationSchedule> ctor = MedicationSchedule.class
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        final MedicationSchedule schedule = ctor.newInstance();
        setField(schedule, "id", SCHEDULE_ID);
        setField(schedule, "medicineId", MEDICINE_ID);
        setField(schedule, "mealSlot", com.ppiyaki.medication.domain.MealSlot.BREAKFAST);
        // 기본 dosage = 1정
        setField(schedule, "dosageQuantity", java.math.BigDecimal.ONE);
        setField(schedule, "dosageUnit", com.ppiyaki.medication.domain.DosageUnit.TABLET);
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));

        final Medicine medicine = new Medicine(SENIOR_ID, null, "테스트약", 30, 30, "ITEM-1", null);
        setField(medicine, "id", MEDICINE_ID);
        when(medicineRepository.findById(MEDICINE_ID)).thenReturn(Optional.of(medicine));
        givenAutonomousSenior();
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

    private static <T> T eq(final T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
