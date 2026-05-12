package com.ppiyaki.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.notification.DevicePlatform;
import com.ppiyaki.notification.DeviceToken;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @InjectMocks
    private DeviceTokenService deviceTokenService;

    @Test
    @DisplayName("신규 token이면 save 호출")
    void register_new_token() {
        // given
        given(deviceTokenRepository.findByToken("token-123")).willReturn(Optional.empty());
        final DeviceToken saved = mock(DeviceToken.class);
        given(saved.getIsActive()).willReturn(true);
        given(deviceTokenRepository.save(any(DeviceToken.class))).willReturn(saved);

        // when
        deviceTokenService.register(7L, "token-123", DevicePlatform.ANDROID);

        // then
        verify(deviceTokenRepository).save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("기존 token + 같은 user면 reactivate 호출 + transferTo는 호출 안 함")
    void register_existing_token_sameUser_reactivates() {
        // given
        final DeviceToken existing = mock(DeviceToken.class);
        given(existing.getUserId()).willReturn(7L);
        given(existing.getIsActive()).willReturn(true);
        given(deviceTokenRepository.findByToken("token-123")).willReturn(Optional.of(existing));

        // when
        deviceTokenService.register(7L, "token-123", DevicePlatform.ANDROID);

        // then
        verify(existing).reactivate(any(LocalDateTime.class));
        verify(existing, never()).transferTo(any(), any(LocalDateTime.class));
        verify(deviceTokenRepository, never()).save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("기존 token + 다른 user면 transferTo 호출 (소유자 이전, issue #329)")
    void register_existing_token_differentUser_transfers() {
        // given — 기존 row owner=7L, 신규 등록 요청 owner=30L (다른 user)
        final DeviceToken existing = mock(DeviceToken.class);
        given(existing.getUserId()).willReturn(7L);
        given(existing.getIsActive()).willReturn(true);
        given(deviceTokenRepository.findByToken("token-123")).willReturn(Optional.of(existing));

        // when
        deviceTokenService.register(30L, "token-123", DevicePlatform.ANDROID);

        // then — transferTo로 owner 이전, reactivate는 호출 안 함
        verify(existing).transferTo(eq(30L), any(LocalDateTime.class));
        verify(existing, never()).reactivate(any(LocalDateTime.class));
        verify(deviceTokenRepository, never()).save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("본인 token deactivate 시 entity.deactivate 호출")
    void deactivate_owner_success() {
        // given
        final DeviceToken token = mock(DeviceToken.class);
        given(token.getUserId()).willReturn(7L);
        given(deviceTokenRepository.findById(1L)).willReturn(Optional.of(token));

        // when
        deviceTokenService.deactivate(7L, 1L);

        // then
        verify(token).deactivate();
    }

    @Test
    @DisplayName("타인 token deactivate 시 DEVICE_TOKEN_FORBIDDEN")
    void deactivate_nonOwner_throwsForbidden() {
        // given — token.userId=7, requester=99
        final DeviceToken token = mock(DeviceToken.class);
        given(token.getUserId()).willReturn(7L);
        given(deviceTokenRepository.findById(1L)).willReturn(Optional.of(token));

        // when & then
        assertThatThrownBy(() -> deviceTokenService.deactivate(99L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEVICE_TOKEN_FORBIDDEN));
    }

    @Test
    @DisplayName("미존재 token deactivate 시 DEVICE_TOKEN_NOT_FOUND")
    void deactivate_notFound() {
        // given
        given(deviceTokenRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> deviceTokenService.deactivate(7L, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEVICE_TOKEN_NOT_FOUND));
    }
}
