package com.ppiyaki.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.medication.MealSlot;
import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.push.PushSender;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
import com.ppiyaki.notification.repository.NotificationRepository;
import com.ppiyaki.notification.repository.NotificationSettingsRepository;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.User;
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
@DisplayName("MedicationDelayDispatcher 메시지 빌더")
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
    @DisplayName("발송 메시지 본문에 약 이름 + 복용량 포함 (issue #345)")
    void body_includes_medicine_name_and_dosage() throws Exception {
        givenSenior();
        givenCareRelation();
        givenSettingsDefault();
        final MedicationSchedule schedule = buildSchedule(46L, 100L, "1정");
        when(scheduleRepository.findActiveByOwnerAndMealSlot(eq(SENIOR_ID), eq(TODAY), eq(MealSlot.BREAKFAST)))
                .thenReturn(List.of(schedule));
        when(scheduleRepository.findActiveByOwnerAndMealSlot(eq(SENIOR_ID), eq(TODAY), eq(MealSlot.LUNCH)))
                .thenReturn(List.of());
        when(scheduleRepository.findActiveByOwnerAndMealSlot(eq(SENIOR_ID), eq(TODAY), eq(MealSlot.DINNER)))
                .thenReturn(List.of());
        when(logRepository.findBySeniorIdAndTargetDate(SENIOR_ID, TODAY)).thenReturn(List.of());
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(buildMedicine(100L, "록스펜씨알정")));
        when(notificationRepository.existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndScheduleId(
                anyLong(), any(), anyLong(), any(), anyLong())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_ID)).thenReturn(List.of());

        dispatcher.dispatchForSenior(seniorUser(), TODAY);

        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        org.mockito.Mockito.verify(notificationRepository).save(captor.capture());
        final Notification saved = captor.getValue();
        assertThat(saved.getBody())
                .isEqualTo("김철수 어르신이 아침에 록스펜씨알정 1정을 아직 복용하지 않았어요. (60분 경과)");
    }

    @Test
    @DisplayName("medicine 조회 실패 시 fallback \"약\"")
    void body_fallback_when_medicine_not_found() throws Exception {
        givenSenior();
        givenCareRelation();
        givenSettingsDefault();
        final MedicationSchedule schedule = buildSchedule(46L, 999L, "2캡슐");
        when(scheduleRepository.findActiveByOwnerAndMealSlot(eq(SENIOR_ID), eq(TODAY), eq(MealSlot.BREAKFAST)))
                .thenReturn(List.of(schedule));
        when(scheduleRepository.findActiveByOwnerAndMealSlot(eq(SENIOR_ID), eq(TODAY), eq(MealSlot.LUNCH)))
                .thenReturn(List.of());
        when(scheduleRepository.findActiveByOwnerAndMealSlot(eq(SENIOR_ID), eq(TODAY), eq(MealSlot.DINNER)))
                .thenReturn(List.of());
        when(logRepository.findBySeniorIdAndTargetDate(SENIOR_ID, TODAY)).thenReturn(List.of());
        when(medicineRepository.findById(999L)).thenReturn(Optional.empty());
        when(notificationRepository.existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndScheduleId(
                anyLong(), any(), anyLong(), any(), anyLong())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_ID)).thenReturn(List.of());

        dispatcher.dispatchForSenior(seniorUser(), TODAY);

        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        org.mockito.Mockito.verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getBody())
                .isEqualTo("김철수 어르신이 아침에 약 2캡슐을 아직 복용하지 않았어요. (60분 경과)");
    }

    @Test
    @DisplayName("dosage가 null이면 약 이름만 표시")
    void body_omits_dosage_when_null() throws Exception {
        givenSenior();
        givenCareRelation();
        givenSettingsDefault();
        final MedicationSchedule schedule = buildSchedule(46L, 100L, null);
        when(scheduleRepository.findActiveByOwnerAndMealSlot(eq(SENIOR_ID), eq(TODAY), eq(MealSlot.BREAKFAST)))
                .thenReturn(List.of(schedule));
        when(scheduleRepository.findActiveByOwnerAndMealSlot(eq(SENIOR_ID), eq(TODAY), eq(MealSlot.LUNCH)))
                .thenReturn(List.of());
        when(scheduleRepository.findActiveByOwnerAndMealSlot(eq(SENIOR_ID), eq(TODAY), eq(MealSlot.DINNER)))
                .thenReturn(List.of());
        when(logRepository.findBySeniorIdAndTargetDate(SENIOR_ID, TODAY)).thenReturn(List.of());
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(buildMedicine(100L, "록스펜씨알정")));
        when(notificationRepository.existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndScheduleId(
                anyLong(), any(), anyLong(), any(), anyLong())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_ID)).thenReturn(List.of());

        dispatcher.dispatchForSenior(seniorUser(), TODAY);

        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        org.mockito.Mockito.verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getBody())
                .isEqualTo("김철수 어르신이 아침에 록스펜씨알정을 아직 복용하지 않았어요. (60분 경과)");
    }

    // --- fixtures ---

    private User seniorUser() {
        final User mockSenior = mock(User.class);
        when(mockSenior.getId()).thenReturn(SENIOR_ID);
        when(mockSenior.getNickname()).thenReturn("김철수");
        // BREAKFAST만 설정 — LUNCH/DINNER는 null이라 dispatcher가 skip
        when(mockSenior.getBreakfastTime()).thenReturn(LocalTime.of(10, 0));
        return mockSenior;
    }

    private void givenSenior() {
        // no-op (User mock에서 mealTimes 직접 stub)
    }

    private void givenCareRelation() {
        final CareRelation rel = mock(CareRelation.class);
        when(rel.getCaregiverId()).thenReturn(CAREGIVER_ID);
        when(careRelationRepository.findBySeniorIdAndDeletedAtIsNull(SENIOR_ID))
                .thenReturn(List.of(rel));
    }

    private void givenSettingsDefault() {
        when(settingsRepository.findByCaregiverIdAndSeniorId(CAREGIVER_ID, SENIOR_ID))
                .thenReturn(Optional.empty()); // default threshold 60
    }

    private MedicationSchedule buildSchedule(final Long id, final Long medicineId,
            final String dosage) throws Exception {
        final java.lang.reflect.Constructor<MedicationSchedule> ctor = MedicationSchedule.class
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        final MedicationSchedule s = ctor.newInstance();
        setField(s, "id", id);
        setField(s, "medicineId", medicineId);
        setField(s, "mealSlot", MealSlot.BREAKFAST);
        setField(s, "dosage", dosage);
        return s;
    }

    private Medicine buildMedicine(final Long id, final String name) throws Exception {
        final Medicine m = new Medicine(SENIOR_ID, null, name, 30, 30, "ITEM-" + id, null);
        setField(m, "id", id);
        return m;
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
