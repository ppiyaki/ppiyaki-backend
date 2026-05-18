package com.ppiyaki.medication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ppiyaki.infrastructure.ai.OpenAiClient;
import com.ppiyaki.infrastructure.storage.NcpStorageProperties;
import com.ppiyaki.infrastructure.storage.PhotoUrlAssembler;
import com.ppiyaki.medication.controller.dto.MedicationLogUpsertRequest;
import com.ppiyaki.medication.domain.LogPillCountStatus;
import com.ppiyaki.medication.domain.LogStatus;
import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.MedicationLog;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.repository.CareRelationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicationLogService Phase 2 약 개수 AI 검증")
class MedicationLogServicePhase2Test {

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
    @Mock
    private OpenAiClient openAiClient;
    @Mock
    private NcpStorageProperties storageProperties;
    @Mock
    private S3Client s3Client;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private com.ppiyaki.notification.repository.NotificationRepository notificationRepository;
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private MedicationLogService service;

    private static final Long SENIOR_ID = 100L;
    private static final Long SCHEDULE_ID = 1L;
    private static final Long MEDICINE_ID = 10L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 4, 30);
    private static final MealSlot MEAL_SLOT = MealSlot.BREAKFAST;
    private static final String VALID_OBJECT_KEY = "medication-log/100/9b3e7a1c-8d55-4f0a-b2e1-5f9a7b3d8c21.jpg";

    @Test
    @DisplayName("status=MISSED면 AI 검증 스킵, pill_count_status=null")
    void status_missed_skip_verification() throws Exception {
        givenScheduleAndMedicine();
        givenUpsertSucceeds();
        final MedicationLogUpsertRequest req = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.MISSED, VALID_OBJECT_KEY);

        service.upsert(SENIOR_ID, req);

        verify(openAiClient, never()).countPills(any(), any());
    }

    @Test
    @DisplayName("photoObjectKey=null이면 AI 검증 스킵")
    void no_photo_skip_verification() throws Exception {
        givenScheduleAndMedicine();
        givenUpsertSucceeds();
        final MedicationLogUpsertRequest req = new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, null);

        service.upsert(SENIOR_ID, req);

        verify(openAiClient, never()).countPills(any(), any());
    }

    @Test
    @DisplayName("단일 schedule(1정) + Vision=1 → COUNT_MATCH")
    void count_match_single_schedule() throws Exception {
        givenScheduleAndMedicine();
        givenUpsertSucceeds();
        givenSiblingSchedules(List.of(buildSchedule(SCHEDULE_ID, "1정")));
        givenS3Returns(new byte[]{1, 2, 3});
        when(openAiClient.countPills(any(), eq("image/jpeg"))).thenReturn(Optional.of(1));

        final var saved = captureSavedLog();
        service.upsert(SENIOR_ID, new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, VALID_OBJECT_KEY));

        assertThat(saved.get().getPillCountStatus()).isEqualTo(LogPillCountStatus.COUNT_MATCH);
    }

    @Test
    @DisplayName("두 schedule(1정+1정) + Vision=1 → COUNT_MISMATCH")
    void count_mismatch_two_schedules() throws Exception {
        givenScheduleAndMedicine();
        givenUpsertSucceeds();
        givenSiblingSchedules(List.of(buildSchedule(SCHEDULE_ID, "1정"), buildSchedule(2L, "1정")));
        givenS3Returns(new byte[]{1, 2, 3});
        when(openAiClient.countPills(any(), any())).thenReturn(Optional.of(1));

        final var saved = captureSavedLog();
        service.upsert(SENIOR_ID, new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, VALID_OBJECT_KEY));

        assertThat(saved.get().getPillCountStatus()).isEqualTo(LogPillCountStatus.COUNT_MISMATCH);
    }

    @Test
    @DisplayName("dosage=\"반알\" 포함 → COUNT_UNKNOWN, Vision 호출 안 됨")
    void unparsable_dosage_unknown() throws Exception {
        givenScheduleAndMedicine();
        givenUpsertSucceeds();
        givenSiblingSchedules(List.of(buildSchedule(SCHEDULE_ID, "1정"), buildSchedule(2L, "반알")));

        final var saved = captureSavedLog();
        service.upsert(SENIOR_ID, new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, VALID_OBJECT_KEY));

        assertThat(saved.get().getPillCountStatus()).isEqualTo(LogPillCountStatus.COUNT_UNKNOWN);
        verify(openAiClient, never()).countPills(any(), any());
    }

    @Test
    @DisplayName("Vision Optional.empty() → COUNT_FAILED")
    void vision_empty_failed() throws Exception {
        givenScheduleAndMedicine();
        givenUpsertSucceeds();
        givenSiblingSchedules(List.of(buildSchedule(SCHEDULE_ID, "1정")));
        givenS3Returns(new byte[]{1});
        when(openAiClient.countPills(any(), any())).thenReturn(Optional.empty());

        final var saved = captureSavedLog();
        service.upsert(SENIOR_ID, new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, VALID_OBJECT_KEY));

        assertThat(saved.get().getPillCountStatus()).isEqualTo(LogPillCountStatus.COUNT_FAILED);
    }

    @Test
    @DisplayName("COUNT_MATCH 시 슬롯의 다른 active schedule도 TAKEN 전파 (issue #343)")
    void count_match_propagates_to_peers() throws Exception {
        // given: trigger schedule(1정) + peer schedule(2L, 1정), Vision=2 → COUNT_MATCH
        final Long peerScheduleId = 2L;
        final Long peerMedicineId = 11L;
        final MedicationSchedule triggerSchedule = buildSchedule(SCHEDULE_ID, "1정");
        final MedicationSchedule peerSchedule = buildSchedule(peerScheduleId, "1정");
        setField(peerSchedule, "medicineId", peerMedicineId);

        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(triggerSchedule));
        final Medicine triggerMedicine = new Medicine(SENIOR_ID, null, "트리거약", 30, 30, "ITEM-1", null);
        setField(triggerMedicine, "id", MEDICINE_ID);
        when(medicineRepository.findById(MEDICINE_ID)).thenReturn(Optional.of(triggerMedicine));
        final Medicine peerMedicine = new Medicine(SENIOR_ID, null, "피어약", 30, 30, "ITEM-2", null);
        setField(peerMedicine, "id", peerMedicineId);
        when(medicineRepository.findById(peerMedicineId)).thenReturn(Optional.of(peerMedicine));

        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.findByScheduleIdAndTargetDate(peerScheduleId, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn("https://example.com/" + VALID_OBJECT_KEY);
        givenSiblingSchedules(List.of(triggerSchedule, peerSchedule));
        givenS3Returns(new byte[]{1, 2, 3});
        when(openAiClient.countPills(any(), any())).thenReturn(Optional.of(2));

        // when
        service.upsert(SENIOR_ID, new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, VALID_OBJECT_KEY));

        // then — trigger + peer 모두 TAKEN log 저장 (saveAndFlush 2번)
        final ArgumentCaptor<MedicationLog> captor = ArgumentCaptor.forClass(MedicationLog.class);
        verify(medicationLogRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        final List<MedicationLog> savedLogs = captor.getAllValues();
        assertThat(savedLogs).hasSize(2);
        assertThat(savedLogs).allMatch(l -> l.getStatus() == LogStatus.TAKEN);
        assertThat(savedLogs.stream().map(MedicationLog::getScheduleId))
                .containsExactlyInAnyOrder(SCHEDULE_ID, peerScheduleId);
        // peer의 medicine 잔여분도 차감 (30 → 29)
        assertThat(peerMedicine.getRemainingAmount()).isEqualTo(29);
    }

    @Test
    @DisplayName("COUNT_MISMATCH 시 슬롯의 다른 schedule에 전파 안 함")
    void count_mismatch_no_propagate() throws Exception {
        givenScheduleAndMedicine();
        givenUpsertSucceeds();
        givenSiblingSchedules(List.of(buildSchedule(SCHEDULE_ID, "1정"), buildSchedule(2L, "1정")));
        givenS3Returns(new byte[]{1});
        when(openAiClient.countPills(any(), any())).thenReturn(Optional.of(1));

        service.upsert(SENIOR_ID, new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, VALID_OBJECT_KEY));

        // saveAndFlush는 trigger schedule 1번만 호출 (peer는 호출 X)
        verify(medicationLogRepository, org.mockito.Mockito.times(1)).saveAndFlush(any(MedicationLog.class));
    }

    @Test
    @DisplayName("COUNT_MATCH지만 peer가 이미 TAKEN이면 skip (멱등)")
    void count_match_skip_already_taken_peer() throws Exception {
        // given
        final Long peerScheduleId = 2L;
        final Long peerMedicineId = 11L;
        final MedicationSchedule triggerSchedule = buildSchedule(SCHEDULE_ID, "1정");
        final MedicationSchedule peerSchedule = buildSchedule(peerScheduleId, "1정");
        setField(peerSchedule, "medicineId", peerMedicineId);

        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(triggerSchedule));
        final Medicine triggerMedicine = new Medicine(SENIOR_ID, null, "트리거약", 30, 30, "ITEM-1", null);
        setField(triggerMedicine, "id", MEDICINE_ID);
        when(medicineRepository.findById(MEDICINE_ID)).thenReturn(Optional.of(triggerMedicine));
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        // peer는 이미 TAKEN log 있음
        final MedicationLog existingPeerLog = new MedicationLog(
                SENIOR_ID, peerScheduleId, TARGET_DATE,
                java.time.LocalDateTime.of(TARGET_DATE, java.time.LocalTime.of(8, 0)),
                LogStatus.TAKEN, null, false, SENIOR_ID);
        when(medicationLogRepository.findByScheduleIdAndTargetDate(peerScheduleId, TARGET_DATE))
                .thenReturn(Optional.of(existingPeerLog));
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn("https://example.com/" + VALID_OBJECT_KEY);
        givenSiblingSchedules(List.of(triggerSchedule, peerSchedule));
        givenS3Returns(new byte[]{1, 2, 3});
        when(openAiClient.countPills(any(), any())).thenReturn(Optional.of(2));

        // when
        service.upsert(SENIOR_ID, new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, VALID_OBJECT_KEY));

        // then — trigger schedule만 save, peer는 이미 TAKEN이라 skip
        verify(medicationLogRepository, org.mockito.Mockito.times(1)).saveAndFlush(any(MedicationLog.class));
    }

    @Test
    @DisplayName("png objectKey면 mediaType=image/png 전달")
    void png_media_type() throws Exception {
        givenScheduleAndMedicine();
        givenUpsertSucceeds();
        givenSiblingSchedules(List.of(buildSchedule(SCHEDULE_ID, "1정")));
        givenS3Returns(new byte[]{1});
        when(openAiClient.countPills(any(), any())).thenReturn(Optional.of(1));

        final String pngKey = "medication-log/100/9b3e7a1c-8d55-4f0a-b2e1-5f9a7b3d8c21.png";
        service.upsert(SENIOR_ID, new MedicationLogUpsertRequest(
                SCHEDULE_ID, TARGET_DATE, null, LogStatus.TAKEN, pngKey));

        verify(openAiClient).countPills(any(), eq("image/png"));
    }

    // --- fixtures -----------------------------------------------------------

    private void givenScheduleAndMedicine() throws Exception {
        final MedicationSchedule schedule = buildSchedule(SCHEDULE_ID, "1정");
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        final Medicine medicine = new Medicine(SENIOR_ID, null, "테스트약", 30, 30, "ITEM-1", null);
        setField(medicine, "id", MEDICINE_ID);
        when(medicineRepository.findById(MEDICINE_ID)).thenReturn(Optional.of(medicine));
    }

    private void givenSiblingSchedules(final List<MedicationSchedule> schedules) {
        when(medicationScheduleRepository.findActiveByOwnerAndMealSlot(
                eq(SENIOR_ID), eq(TARGET_DATE), eq(MEAL_SLOT))).thenReturn(schedules);
    }

    private void givenUpsertSucceeds() {
        when(medicationLogRepository.findByScheduleIdAndTargetDate(SCHEDULE_ID, TARGET_DATE))
                .thenReturn(Optional.empty());
        when(medicationLogRepository.saveAndFlush(any(MedicationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn("https://example.com/" + VALID_OBJECT_KEY);
    }

    private void givenS3Returns(final byte[] bytes) {
        when(storageProperties.bucketName()).thenReturn("ppiyaki-test");
        final ResponseBytes<GetObjectResponse> resp = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), bytes);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(resp);
    }

    private java.util.function.Supplier<MedicationLog> captureSavedLog() {
        final ArgumentCaptor<MedicationLog> captor = ArgumentCaptor.forClass(MedicationLog.class);
        return () -> {
            verify(medicationLogRepository).saveAndFlush(captor.capture());
            return captor.getValue();
        };
    }

    private MedicationSchedule buildSchedule(final Long id, final String dosage) throws Exception {
        final java.lang.reflect.Constructor<MedicationSchedule> ctor = MedicationSchedule.class
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        final MedicationSchedule s = ctor.newInstance();
        setField(s, "id", id);
        setField(s, "medicineId", MEDICINE_ID);
        setField(s, "mealSlot", MEAL_SLOT);
        if (dosage != null) {
            final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)(.*)$")
                    .matcher(dosage);
            if (m.find()) {
                setField(s, "dosageQuantity", new java.math.BigDecimal(m.group(1)));
                setField(s, "dosageUnit",
                        com.ppiyaki.medication.domain.DosageUnit.fromInput(m.group(2).trim()).orElse(null));
            }
        }
        return s;
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
