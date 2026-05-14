package com.ppiyaki.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ppiyaki.infrastructure.messaging.fcm.PushSendResult;
import com.ppiyaki.infrastructure.messaging.fcm.PushSender;
import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.NotificationCategory;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
import com.ppiyaki.notification.repository.NotificationRepository;
import com.ppiyaki.user.domain.User;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicationReminderDispatcherTest {

    @Mock
    private MedicationScheduleRepository medicationScheduleRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private PushSender pushSender;

    @InjectMocks
    private MedicationReminderDispatcher dispatcher;

    @Test
    @DisplayName("이미 발송된 알림이 있으면 skip")
    void dispatch_skip_when_existing() {
        // given
        final User senior = mock(User.class);
        given(senior.getId()).willReturn(7L);
        given(notificationRepository.existsByUserIdAndCategoryAndTargetDateAndMealSlot(
                7L, NotificationCategory.MEDICATION_REMINDER, LocalDate.now(), MealSlot.BREAKFAST.name()))
                .willReturn(true);

        // when
        final boolean dispatched = dispatcher.dispatchIfDue(senior, MealSlot.BREAKFAST, LocalDate.now());

        // then
        assertThat(dispatched).isFalse();
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(pushSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("active schedule 없으면 skip")
    void dispatch_skip_when_no_active_schedule() {
        // given
        final User senior = mock(User.class);
        given(senior.getId()).willReturn(7L);
        given(medicationScheduleRepository.findActiveByOwnerAndMealSlot(anyLong(), any(LocalDate.class), any(
                MealSlot.class)))
                .willReturn(List.of());

        // when
        final boolean dispatched = dispatcher.dispatchIfDue(senior, MealSlot.BREAKFAST, LocalDate.now());

        // then
        assertThat(dispatched).isFalse();
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("active schedule 있고 미발송이면 발송 + 활성 device tokens에 push")
    void dispatch_success() {
        // given
        final User senior = mock(User.class);
        given(senior.getId()).willReturn(7L);

        final MedicationSchedule schedule = mock(MedicationSchedule.class);
        given(medicationScheduleRepository.findActiveByOwnerAndMealSlot(anyLong(), any(LocalDate.class), any(
                MealSlot.class)))
                .willReturn(List.of(schedule));

        final Notification saved = mock(Notification.class);
        given(saved.getId()).willReturn(101L);
        given(notificationRepository.save(any(Notification.class))).willReturn(saved);

        given(deviceTokenRepository.findByUserIdAndIsActiveTrue(7L)).willReturn(List.of());

        // when
        final boolean dispatched = dispatcher.dispatchIfDue(senior, MealSlot.BREAKFAST, LocalDate.now());

        // then
        assertThat(dispatched).isTrue();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("push 응답이 tokenInvalid면 device token deactivate")
    void dispatch_deactivates_invalid_token() {
        // given
        final User senior = mock(User.class);
        given(senior.getId()).willReturn(7L);

        final MedicationSchedule schedule = mock(MedicationSchedule.class);
        given(medicationScheduleRepository.findActiveByOwnerAndMealSlot(anyLong(), any(LocalDate.class), any(
                MealSlot.class)))
                .willReturn(List.of(schedule));

        final Notification saved = mock(Notification.class);
        given(saved.getId()).willReturn(101L);
        given(notificationRepository.save(any(Notification.class))).willReturn(saved);

        final com.ppiyaki.notification.DeviceToken token = mock(com.ppiyaki.notification.DeviceToken.class);
        given(token.getToken()).willReturn("invalid-fcm-token");
        given(deviceTokenRepository.findByUserIdAndIsActiveTrue(7L)).willReturn(List.of(token));
        given(pushSender.send(any(), any())).willReturn(PushSendResult.tokenInvalid("UNREGISTERED"));

        // when
        dispatcher.dispatchIfDue(senior, MealSlot.BREAKFAST, LocalDate.now());

        // then
        verify(token).deactivate();
    }
}
