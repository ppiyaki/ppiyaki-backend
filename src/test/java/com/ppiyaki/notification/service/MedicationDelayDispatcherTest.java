package com.ppiyaki.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ppiyaki.infrastructure.messaging.fcm.PushPayload;
import com.ppiyaki.infrastructure.messaging.fcm.PushSendResult;
import com.ppiyaki.infrastructure.messaging.fcm.PushSender;
import com.ppiyaki.medication.domain.LogStatus;
import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.MedicationLog;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.notification.DeviceToken;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
import com.ppiyaki.notification.repository.NotificationRepository;
import com.ppiyaki.notification.repository.NotificationSettingsRepository;
import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MedicationDelayDispatcher 슬롯 단위 묶음 발송")
class MedicationDelayDispatcherTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CareRelationRepository careRelationRepository;
    @Mock
    private MedicationScheduleRepository scheduleRepository;
    @Mock
    private MedicationLogRepository logRepository;
    @Mock
    private MedicineRepository medicineRepository;
    @Mock
    private NotificationSettingsRepository settingsRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private PushSender pushSender;

    @Spy
    private Clock clock = Clock.fixed(
            Instant.parse("2026-05-13T11:00:30Z"), ZoneId.of("UTC"));

    @InjectMocks
    private MedicationDelayDispatcher dispatcher;

    private static final Long SENIOR_ID = 38L;
    private static final Long CAREGIVER_ID = 37L;
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 13);

    @Test
    @DisplayName("미인증 약 1개도 묶음 포맷으로 발송 (issue #409)")
    void single_unsent_uses_grouped_format() throws Exception {
        givenCareRelation();
        givenSettingsDefault();
        final MedicationSchedule schedule = buildSchedule(46L, 100L, "1정");
        givenSlotSchedules(MealSlot.BREAKFAST, List.of(schedule));
        givenEmptyOtherSlots();
        when(logRepository.findBySeniorIdAndTargetDate(SENIOR_ID, TODAY)).thenReturn(List.of());
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(buildMedicine(100L, "록스펜씨알정")));
        givenNoExistingDelayNotification();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_ID)).thenReturn(List.of());

        final int dispatched = dispatcher.dispatchForSenior(seniorUser(), TODAY);

        assertThat(dispatched).isEqualTo(1);
        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getBody())
                .isEqualTo("김철수 어르신이 아침에 약 1개를 아직 복용하지 않았어요. (60분 경과)\n• 록스펜씨알정 1정");
    }

    @Test
    @DisplayName("medicine 조회 실패 시 fallback \"약\" + 묶음 포맷")
    void body_fallback_when_medicine_not_found() throws Exception {
        givenCareRelation();
        givenSettingsDefault();
        final MedicationSchedule schedule = buildSchedule(46L, 999L, "2캡슐");
        givenSlotSchedules(MealSlot.BREAKFAST, List.of(schedule));
        givenEmptyOtherSlots();
        when(logRepository.findBySeniorIdAndTargetDate(SENIOR_ID, TODAY)).thenReturn(List.of());
        when(medicineRepository.findById(999L)).thenReturn(Optional.empty());
        givenNoExistingDelayNotification();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_ID)).thenReturn(List.of());

        dispatcher.dispatchForSenior(seniorUser(), TODAY);

        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getBody())
                .isEqualTo("김철수 어르신이 아침에 약 1개를 아직 복용하지 않았어요. (60분 경과)\n• 약 2캡슐");
    }

    @Test
    @DisplayName("dosage가 null이면 약 이름만 표시")
    void body_omits_dosage_when_null() throws Exception {
        givenCareRelation();
        givenSettingsDefault();
        final MedicationSchedule schedule = buildSchedule(46L, 100L, null);
        givenSlotSchedules(MealSlot.BREAKFAST, List.of(schedule));
        givenEmptyOtherSlots();
        when(logRepository.findBySeniorIdAndTargetDate(SENIOR_ID, TODAY)).thenReturn(List.of());
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(buildMedicine(100L, "록스펜씨알정")));
        givenNoExistingDelayNotification();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_ID)).thenReturn(List.of());

        dispatcher.dispatchForSenior(seniorUser(), TODAY);

        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getBody())
                .isEqualTo("김철수 어르신이 아침에 약 1개를 아직 복용하지 않았어요. (60분 경과)\n• 록스펜씨알정");
    }

    @Test
    @DisplayName("한 슬롯 미인증 3개 → 알림 1건 묶음 발송 + payload scheduleIds JSON 배열")
    void groups_multiple_unsent_into_single_notification() throws Exception {
        givenCareRelation();
        givenSettingsDefault();
        final MedicationSchedule s1 = buildScheduleWithId(101L, 1001L, "1정");
        final MedicationSchedule s2 = buildScheduleWithId(102L, 1002L, "2캡슐");
        final MedicationSchedule s3 = buildScheduleWithId(103L, 1003L, null);
        givenSlotSchedules(MealSlot.BREAKFAST, List.of(s1, s2, s3));
        givenEmptyOtherSlots();
        when(logRepository.findBySeniorIdAndTargetDate(SENIOR_ID, TODAY)).thenReturn(List.of());
        when(medicineRepository.findById(1001L)).thenReturn(Optional.of(buildMedicine(1001L, "타이레놀500mg")));
        when(medicineRepository.findById(1002L)).thenReturn(Optional.of(buildMedicine(1002L, "비타민C")));
        when(medicineRepository.findById(1003L)).thenReturn(Optional.of(buildMedicine(1003L, "위장약")));
        givenNoExistingDelayNotification();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_ID))
                .thenReturn(List.of(buildDeviceToken("token-xyz")));
        when(pushSender.send(anyString(), any(PushPayload.class)))
                .thenReturn(new PushSendResult(true, false, null));

        final int dispatched = dispatcher.dispatchForSenior(seniorUser(), TODAY);

        assertThat(dispatched).isEqualTo(1);
        final ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getBody())
                .isEqualTo("김철수 어르신이 아침에 약 3개를 아직 복용하지 않았어요. (60분 경과)"
                        + "\n• 타이레놀500mg 1정"
                        + "\n• 비타민C 2캡슐"
                        + "\n• 위장약");
        assertThat(notificationCaptor.getValue().getScheduleId()).isNull();

        final ArgumentCaptor<PushPayload> payloadCaptor = ArgumentCaptor.forClass(PushPayload.class);
        verify(pushSender, times(1)).send(eq("token-xyz"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().data())
                .containsEntry("category", "MEDICATION_DELAY")
                .containsEntry("seniorId", String.valueOf(SENIOR_ID))
                .containsEntry("mealSlot", MealSlot.BREAKFAST.name())
                .containsEntry("scheduleIds", "[101,102,103]");
    }

    @Test
    @DisplayName("일부만 인증된 경우 미인증 schedule만 본문에 포함")
    void excludes_taken_schedules_from_body() throws Exception {
        givenCareRelation();
        givenSettingsDefault();
        final MedicationSchedule s1 = buildScheduleWithId(101L, 1001L, "1정");
        final MedicationSchedule s2 = buildScheduleWithId(102L, 1002L, "2캡슐");
        givenSlotSchedules(MealSlot.BREAKFAST, List.of(s1, s2));
        givenEmptyOtherSlots();
        when(logRepository.findBySeniorIdAndTargetDate(SENIOR_ID, TODAY))
                .thenReturn(List.of(buildTakenLog(101L)));
        when(medicineRepository.findById(1002L)).thenReturn(Optional.of(buildMedicine(1002L, "비타민C")));
        givenNoExistingDelayNotification();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_ID)).thenReturn(List.of());

        dispatcher.dispatchForSenior(seniorUser(), TODAY);

        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getBody())
                .isEqualTo("김철수 어르신이 아침에 약 1개를 아직 복용하지 않았어요. (60분 경과)\n• 비타민C 2캡슐");
    }

    @Test
    @DisplayName("슬롯의 모든 schedule이 인증된 경우 알림 발송 없음")
    void no_notification_when_all_taken() throws Exception {
        givenCareRelation();
        givenSettingsDefault();
        final MedicationSchedule s1 = buildScheduleWithId(101L, 1001L, "1정");
        givenSlotSchedules(MealSlot.BREAKFAST, List.of(s1));
        givenEmptyOtherSlots();
        when(logRepository.findBySeniorIdAndTargetDate(SENIOR_ID, TODAY))
                .thenReturn(List.of(buildTakenLog(101L)));

        final int dispatched = dispatcher.dispatchForSenior(seniorUser(), TODAY);

        assertThat(dispatched).isZero();
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("같은 slot+date에 이미 발송된 경우 재발송 안 함 (멱등)")
    void idempotent_per_slot_and_date() throws Exception {
        givenCareRelation();
        givenSettingsDefault();
        final MedicationSchedule s1 = buildScheduleWithId(101L, 1001L, "1정");
        givenSlotSchedules(MealSlot.BREAKFAST, List.of(s1));
        givenEmptyOtherSlots();
        when(logRepository.findBySeniorIdAndTargetDate(SENIOR_ID, TODAY)).thenReturn(List.of());
        when(notificationRepository.existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndMealSlot(
                anyLong(), any(), anyLong(), any(), anyString())).thenReturn(true);

        final int dispatched = dispatcher.dispatchForSenior(seniorUser(), TODAY);

        assertThat(dispatched).isZero();
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    // --- fixtures ---

    private User seniorUser() {
        final User mockSenior = mock(User.class);
        when(mockSenior.getId()).thenReturn(SENIOR_ID);
        when(mockSenior.getNickname()).thenReturn("김철수");
        when(mockSenior.getBreakfastTime()).thenReturn(LocalTime.of(10, 0));
        return mockSenior;
    }

    private void givenCareRelation() {
        final CareRelation rel = mock(CareRelation.class);
        when(rel.getCaregiverId()).thenReturn(CAREGIVER_ID);
        when(careRelationRepository.findBySeniorIdAndDeletedAtIsNull(SENIOR_ID))
                .thenReturn(List.of(rel));
    }

    private void givenSettingsDefault() {
        when(settingsRepository.findByCaregiverIdAndSeniorId(CAREGIVER_ID, SENIOR_ID))
                .thenReturn(Optional.empty());
    }

    private void givenSlotSchedules(final MealSlot slot, final List<MedicationSchedule> schedules) {
        when(scheduleRepository.findActiveByOwnerAndMealSlot(eq(SENIOR_ID), eq(TODAY), eq(slot)))
                .thenReturn(schedules);
    }

    private void givenEmptyOtherSlots() {
        when(scheduleRepository.findActiveByOwnerAndMealSlot(eq(SENIOR_ID), eq(TODAY), eq(MealSlot.LUNCH)))
                .thenReturn(List.of());
        when(scheduleRepository.findActiveByOwnerAndMealSlot(eq(SENIOR_ID), eq(TODAY), eq(MealSlot.DINNER)))
                .thenReturn(List.of());
    }

    private void givenNoExistingDelayNotification() {
        when(notificationRepository.existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndMealSlot(
                anyLong(), any(), anyLong(), any(), anyString())).thenReturn(false);
    }

    private MedicationSchedule buildSchedule(final Long id, final Long medicineId,
            final String dosage) throws Exception {
        return buildScheduleWithId(id, medicineId, dosage);
    }

    private MedicationSchedule buildScheduleWithId(final Long id, final Long medicineId,
            final String dosage) throws Exception {
        final java.lang.reflect.Constructor<MedicationSchedule> ctor = MedicationSchedule.class
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        final MedicationSchedule s = ctor.newInstance();
        setField(s, "id", id);
        setField(s, "medicineId", medicineId);
        setField(s, "mealSlot", MealSlot.BREAKFAST);
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

    private Medicine buildMedicine(final Long id, final String name) throws Exception {
        final Medicine m = new Medicine(SENIOR_ID, null, name, 30, 30, "ITEM-" + id, null);
        setField(m, "id", id);
        return m;
    }

    private MedicationLog buildTakenLog(final Long scheduleId) throws Exception {
        final java.lang.reflect.Constructor<MedicationLog> ctor = MedicationLog.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        final MedicationLog log = ctor.newInstance();
        setField(log, "scheduleId", scheduleId);
        setField(log, "status", LogStatus.TAKEN);
        return log;
    }

    private DeviceToken buildDeviceToken(final String token) throws Exception {
        final java.lang.reflect.Constructor<DeviceToken> ctor = DeviceToken.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        final DeviceToken dt = ctor.newInstance();
        setField(dt, "token", token);
        setField(dt, "isActive", true);
        return dt;
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
