package com.ppiyaki.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ppiyaki.infrastructure.messaging.fcm.PushPayload;
import com.ppiyaki.infrastructure.messaging.fcm.PushSendResult;
import com.ppiyaki.infrastructure.messaging.fcm.PushSender;
import com.ppiyaki.notification.DeviceToken;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.NotificationSettings;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
import com.ppiyaki.notification.repository.NotificationRepository;
import com.ppiyaki.notification.repository.NotificationSettingsRepository;
import com.ppiyaki.prescription.event.PrescriptionReviewRequestedEvent;
import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.domain.UserRole;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrescriptionReviewRequestDispatcher")
class PrescriptionReviewRequestDispatcherTest {

    private static final long SENIOR_ID = 100L;
    private static final long PRESCRIPTION_ID = 500L;
    private static final long CAREGIVER_A = 200L;
    private static final long CAREGIVER_B = 201L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private CareRelationRepository careRelationRepository;
    @Mock
    private NotificationSettingsRepository settingsRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private PushSender pushSender;

    @InjectMocks
    private PrescriptionReviewRequestDispatcher dispatcher;

    @Test
    @DisplayName("MANAGED 시니어 + 보호자 2명 → notification row 2건 + 푸시 호출")
    void dispatches_to_all_caregivers_when_managed() {
        final User senior = mockSenior(CareMode.MANAGED, "김장군");
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));

        final CareRelation relationA = mock(CareRelation.class);
        given(relationA.getCaregiverId()).willReturn(CAREGIVER_A);
        final CareRelation relationB = mock(CareRelation.class);
        given(relationB.getCaregiverId()).willReturn(CAREGIVER_B);
        given(careRelationRepository.findBySeniorIdAndDeletedAtIsNull(SENIOR_ID))
                .willReturn(List.of(relationA, relationB));

        given(settingsRepository.findByCaregiverIdAndSeniorId(CAREGIVER_A, SENIOR_ID))
                .willReturn(Optional.empty());
        given(settingsRepository.findByCaregiverIdAndSeniorId(CAREGIVER_B, SENIOR_ID))
                .willReturn(Optional.empty());

        final DeviceToken tokenA = mock(DeviceToken.class);
        given(tokenA.getToken()).willReturn("tok-a");
        given(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_A)).willReturn(List.of(tokenA));
        given(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_B)).willReturn(List.of());
        given(pushSender.send(anyString(), any(PushPayload.class))).willReturn(PushSendResult.ok());

        dispatcher.handle(new PrescriptionReviewRequestedEvent(PRESCRIPTION_ID, SENIOR_ID));

        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(pushSender, times(1)).send(eq("tok-a"), any(PushPayload.class));
    }

    @Test
    @DisplayName("AUTONOMOUS 시니어 → 발송 없음")
    void skips_when_autonomous() {
        final User senior = mockSenior(CareMode.AUTONOMOUS, "박여사");
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));

        dispatcher.handle(new PrescriptionReviewRequestedEvent(PRESCRIPTION_ID, SENIOR_ID));

        verify(careRelationRepository, never()).findBySeniorIdAndDeletedAtIsNull(any());
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(pushSender, never()).send(anyString(), any(PushPayload.class));
    }

    @Test
    @DisplayName("owner role != SENIOR → 발송 없음 (보호자가 본인 토큰으로 등록한 케이스)")
    void skips_when_owner_is_not_senior() {
        final User notSenior = mock(User.class);
        given(notSenior.getRole()).willReturn(UserRole.CAREGIVER);
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(notSenior));

        dispatcher.handle(new PrescriptionReviewRequestedEvent(PRESCRIPTION_ID, SENIOR_ID));

        verify(careRelationRepository, never()).findBySeniorIdAndDeletedAtIsNull(any());
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("활성 보호자 0명 → 발송 없음")
    void skips_when_no_active_caregivers() {
        final User senior = mockSenior(CareMode.MANAGED, "김장군");
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(careRelationRepository.findBySeniorIdAndDeletedAtIsNull(SENIOR_ID)).willReturn(List.of());

        dispatcher.handle(new PrescriptionReviewRequestedEvent(PRESCRIPTION_ID, SENIOR_ID));

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(pushSender, never()).send(anyString(), any(PushPayload.class));
    }

    @Test
    @DisplayName("NotificationSettings.prescriptionReviewRequestEnabled=false인 보호자는 skip")
    void skips_caregiver_with_toggle_off() {
        final User senior = mockSenior(CareMode.MANAGED, "김장군");
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));

        final CareRelation relation = mock(CareRelation.class);
        given(relation.getCaregiverId()).willReturn(CAREGIVER_A);
        given(careRelationRepository.findBySeniorIdAndDeletedAtIsNull(SENIOR_ID))
                .willReturn(List.of(relation));

        final NotificationSettings settings = mock(NotificationSettings.class);
        given(settings.isPrescriptionReviewRequestEnabled()).willReturn(false);
        given(settingsRepository.findByCaregiverIdAndSeniorId(CAREGIVER_A, SENIOR_ID))
                .willReturn(Optional.of(settings));

        dispatcher.handle(new PrescriptionReviewRequestedEvent(PRESCRIPTION_ID, SENIOR_ID));

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(pushSender, never()).send(anyString(), any(PushPayload.class));
    }

    @Test
    @DisplayName("tokenInvalid 결과 시 DeviceToken.deactivate 호출")
    void deactivates_invalid_token() {
        final User senior = mockSenior(CareMode.MANAGED, "김장군");
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));

        final CareRelation relation = mock(CareRelation.class);
        given(relation.getCaregiverId()).willReturn(CAREGIVER_A);
        given(careRelationRepository.findBySeniorIdAndDeletedAtIsNull(SENIOR_ID))
                .willReturn(List.of(relation));

        given(settingsRepository.findByCaregiverIdAndSeniorId(CAREGIVER_A, SENIOR_ID))
                .willReturn(Optional.empty());

        final DeviceToken token = mock(DeviceToken.class);
        given(token.getToken()).willReturn("invalid-token");
        given(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_A)).willReturn(List.of(token));
        given(pushSender.send(eq("invalid-token"), any(PushPayload.class)))
                .willReturn(PushSendResult.tokenInvalid("unregistered"));

        dispatcher.handle(new PrescriptionReviewRequestedEvent(PRESCRIPTION_ID, SENIOR_ID));

        verify(token).deactivate();
    }

    private User mockSenior(final CareMode careMode, final String nickname) {
        final User senior = mock(User.class);
        given(senior.getRole()).willReturn(UserRole.SENIOR);
        lenient().when(senior.getCareMode()).thenReturn(careMode);
        lenient().when(senior.getNickname()).thenReturn(nickname);
        return senior;
    }
}
