package com.ppiyaki.medication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.storage.PhotoUrlAssembler;
import com.ppiyaki.medication.LogStatus;
import com.ppiyaki.medication.MedicationLog;
import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.controller.dto.MedicationLogUpsertRequest;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.repository.CareRelationRepository;
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
    private PhotoUrlAssembler photoUrlAssembler;

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
        when(medicationLogRepository.save(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, LocalDateTime.of(2026, 4, 18, 9, 0), LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(SENIOR_ID, request);

        // then
        final ArgumentCaptor<MedicationLog> captor = ArgumentCaptor.forClass(MedicationLog.class);
        verify(medicationLogRepository).save(captor.capture());
        final MedicationLog saved = captor.getValue();
        assertThat(saved.getIsProxy()).isFalse();
        assertThat(saved.getConfirmedByUserId()).isEqualTo(SENIOR_ID);
        assertThat(saved.getStatus()).isEqualTo(LogStatus.TAKEN);
        assertThat(saved.getSeniorId()).isEqualTo(SENIOR_ID);
    }

    @Test
    @DisplayName("보호자 PUT 시 isProxy=true, confirmedByUserId=보호자")
    void 보호자_대리_업서트() throws Exception {
        // given
        givenScheduleAndMedicine();
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(CAREGIVER_ID, SENIOR_ID))
                .thenReturn(Optional.of(new CareRelation(SENIOR_ID, CAREGIVER_ID, "INVITE")));
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.save(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        // when
        medicationLogService.upsert(CAREGIVER_ID, request);

        // then
        final ArgumentCaptor<MedicationLog> captor = ArgumentCaptor.forClass(MedicationLog.class);
        verify(medicationLogRepository).save(captor.capture());
        final MedicationLog saved = captor.getValue();
        assertThat(saved.getIsProxy()).isTrue();
        assertThat(saved.getConfirmedByUserId()).isEqualTo(CAREGIVER_ID);
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
        verify(medicationLogRepository, never()).save(any()); // update via dirty checking, no explicit save
        assertThat(existing.getStatus()).isEqualTo(LogStatus.MISSED);
        assertThat(existing.getTakenAt()).isEqualTo(LocalDateTime.of(2026, 4, 18, 10, 0));
    }

    @Test
    @DisplayName("photoObjectKey의 userId 세그먼트가 요청자와 다르면 INVALID_INPUT")
    void photoObjectKey_userId_불일치() throws Exception {
        // given
        givenScheduleAndMedicine();

        final MedicationLogUpsertRequest request = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN,
                "medication-log/999/uuid.jpg"); // 999 != SENIOR_ID

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
                "medication-log/100/../etc/passwd");

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
                .thenReturn(Optional.of(new CareRelation(SENIOR_ID, CAREGIVER_ID, "INVITE")));
        when(medicationLogRepository.findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(
                eq(SENIOR_ID), any(), any())).thenReturn(List.of());

        // when
        final var resp = medicationLogService.readByPeriod(CAREGIVER_ID, SENIOR_ID, TARGET_DATE, TARGET_DATE);

        // then
        assertThat(resp.responses()).isEmpty();
    }

    private void givenScheduleAndMedicine() throws Exception {
        final java.lang.reflect.Constructor<MedicationSchedule> ctor = MedicationSchedule.class
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        final MedicationSchedule schedule = ctor.newInstance();
        setField(schedule, "id", SCHEDULE_ID);
        setField(schedule, "medicineId", MEDICINE_ID);
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));

        final Medicine medicine = new Medicine(SENIOR_ID, null, "테스트약", 30, 30, "ITEM-1", null);
        setField(medicine, "id", MEDICINE_ID);
        when(medicineRepository.findById(MEDICINE_ID)).thenReturn(Optional.of(medicine));
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
