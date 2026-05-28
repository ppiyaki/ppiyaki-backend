package com.ppiyaki.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.infrastructure.messaging.fcm.PushPayload;
import com.ppiyaki.infrastructure.messaging.fcm.PushSendResult;
import com.ppiyaki.infrastructure.messaging.fcm.PushSender;
import com.ppiyaki.notification.DeviceToken;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
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
class WellbeingPingServiceTest {

    private static final long SENIOR_ID = 10L;
    private static final long CAREGIVER_ID = 20L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private CareRelationRepository careRelationRepository;
    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private WellbeingPingCooldownStore cooldownStore;
    @Mock
    private PushSender pushSender;

    @InjectMocks
    private WellbeingPingService wellbeingPingService;

    @Test
    @DisplayName("정상 발송 — 모든 활성 토큰에 push 호출")
    void send_success_dispatches_to_all_tokens() {
        // given
        final User senior = mockUser(UserRole.SENIOR);
        given(senior.getNickname()).willReturn("김장군");
        final User caregiver = mockUser(UserRole.CAREGIVER);
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(userRepository.findById(CAREGIVER_ID)).willReturn(Optional.of(caregiver));
        given(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(
                CAREGIVER_ID, SENIOR_ID)).willReturn(Optional.of(mock(CareRelation.class)));
        given(cooldownStore.tryAcquire(SENIOR_ID, CAREGIVER_ID)).willReturn(true);

        final DeviceToken token1 = mock(DeviceToken.class);
        final DeviceToken token2 = mock(DeviceToken.class);
        given(token1.getToken()).willReturn("t1");
        given(token2.getToken()).willReturn("t2");
        given(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_ID))
                .willReturn(List.of(token1, token2));
        given(pushSender.send(anyString(), any(PushPayload.class))).willReturn(PushSendResult.ok());

        // when
        wellbeingPingService.send(SENIOR_ID, CAREGIVER_ID);

        // then
        verify(pushSender, times(2)).send(anyString(), any(PushPayload.class));
        verify(token1, never()).deactivate();
        verify(token2, never()).deactivate();
    }

    @Test
    @DisplayName("발신자가 시니어가 아니면 CARE_RELATION_ROLE_MISMATCH")
    void send_caller_not_senior_throws() {
        final User notSenior = mockUser(UserRole.CAREGIVER);
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(notSenior));

        assertThatThrownBy(() -> wellbeingPingService.send(SENIOR_ID, CAREGIVER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo(ErrorCode.CARE_RELATION_ROLE_MISMATCH);
    }

    @Test
    @DisplayName("수신자가 보호자가 아니면 CARE_RELATION_ROLE_MISMATCH")
    void send_receiver_not_caregiver_throws() {
        final User senior = mockUser(UserRole.SENIOR);
        final User wrongRole = mockUser(UserRole.SENIOR);
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(userRepository.findById(CAREGIVER_ID)).willReturn(Optional.of(wrongRole));

        assertThatThrownBy(() -> wellbeingPingService.send(SENIOR_ID, CAREGIVER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo(ErrorCode.CARE_RELATION_ROLE_MISMATCH);
    }

    @Test
    @DisplayName("CareRelation 없으면 CARE_RELATION_NOT_FOUND")
    void send_no_care_relation_throws() {
        final User senior = mockUser(UserRole.SENIOR);
        final User caregiver = mockUser(UserRole.CAREGIVER);
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(userRepository.findById(CAREGIVER_ID)).willReturn(Optional.of(caregiver));
        given(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(
                CAREGIVER_ID, SENIOR_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> wellbeingPingService.send(SENIOR_ID, CAREGIVER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo(ErrorCode.CARE_RELATION_NOT_FOUND);
    }

    @Test
    @DisplayName("쿨다운 차단 시 WellbeingPingCooldownException + retryAfterSeconds 전달")
    void send_cooldown_blocks_with_retry_after() {
        final User senior = mockUser(UserRole.SENIOR);
        final User caregiver = mockUser(UserRole.CAREGIVER);
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(userRepository.findById(CAREGIVER_ID)).willReturn(Optional.of(caregiver));
        given(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(
                CAREGIVER_ID, SENIOR_ID)).willReturn(Optional.of(mock(CareRelation.class)));
        given(cooldownStore.tryAcquire(SENIOR_ID, CAREGIVER_ID)).willReturn(false);
        given(cooldownStore.getRetryAfterSeconds(SENIOR_ID, CAREGIVER_ID))
                .willReturn(Optional.of(33L));

        assertThatThrownBy(() -> wellbeingPingService.send(SENIOR_ID, CAREGIVER_ID))
                .isInstanceOf(WellbeingPingCooldownException.class)
                .extracting(throwable -> ((WellbeingPingCooldownException) throwable).getRetryAfterSeconds())
                .isEqualTo(33L);
    }

    @Test
    @DisplayName("tokenInvalid 결과 시 DeviceToken.deactivate 호출")
    void send_invalid_token_deactivates() {
        final User senior = mockUser(UserRole.SENIOR);
        final User caregiver = mockUser(UserRole.CAREGIVER);
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(userRepository.findById(CAREGIVER_ID)).willReturn(Optional.of(caregiver));
        given(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(
                CAREGIVER_ID, SENIOR_ID)).willReturn(Optional.of(mock(CareRelation.class)));
        given(cooldownStore.tryAcquire(SENIOR_ID, CAREGIVER_ID)).willReturn(true);

        final DeviceToken token = mock(DeviceToken.class);
        given(token.getToken()).willReturn("invalid-t");
        given(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_ID))
                .willReturn(List.of(token));
        given(pushSender.send(eq("invalid-t"), any(PushPayload.class)))
                .willReturn(PushSendResult.tokenInvalid("unregistered"));

        wellbeingPingService.send(SENIOR_ID, CAREGIVER_ID);

        verify(token).deactivate();
    }

    @Test
    @DisplayName("활성 토큰 0개여도 정상 종료")
    void send_no_tokens_completes() {
        final User senior = mockUser(UserRole.SENIOR);
        final User caregiver = mockUser(UserRole.CAREGIVER);
        given(userRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(userRepository.findById(CAREGIVER_ID)).willReturn(Optional.of(caregiver));
        given(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(
                CAREGIVER_ID, SENIOR_ID)).willReturn(Optional.of(mock(CareRelation.class)));
        given(cooldownStore.tryAcquire(SENIOR_ID, CAREGIVER_ID)).willReturn(true);
        given(deviceTokenRepository.findByUserIdAndIsActiveTrue(CAREGIVER_ID))
                .willReturn(List.of());

        wellbeingPingService.send(SENIOR_ID, CAREGIVER_ID);

        verify(pushSender, never()).send(anyString(), any(PushPayload.class));
    }

    private User mockUser(final UserRole role) {
        final User user = mock(User.class);
        given(user.getRole()).willReturn(role);
        return user;
    }
}
