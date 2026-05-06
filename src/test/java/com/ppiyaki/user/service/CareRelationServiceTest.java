package com.ppiyaki.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ppiyaki.common.auth.JwtProvider;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.ratelimit.RateLimiter;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.InviteCode;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.controller.dto.InviteCodeResponse;
import com.ppiyaki.user.controller.dto.LoginResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.InviteCodeRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareRelationServiceTest {

    @Mock
    private CareRelationRepository careRelationRepository;

    @Mock
    private InviteCodeRepository inviteCodeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AuthService authService;

    @Mock
    private RateLimiter rateLimiter;

    @InjectMocks
    private CareRelationService careRelationService;

    @Test
    @DisplayName("보호자가 시니어의 초대 코드를 발급하면 6자리 코드를 반환한다")
    void createInviteCode_success() {
        // given
        final User caregiver = mockUser(1L, UserRole.CAREGIVER);
        given(userRepository.findById(1L)).willReturn(Optional.of(caregiver));

        final CareRelation existingRelation = CareRelation.createLinked(2L, 1L);
        given(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(1L, 2L))
                .willReturn(Optional.of(existingRelation));
        given(inviteCodeRepository.save(any(InviteCode.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        final InviteCodeResponse response = careRelationService.createInviteCode(1L, 2L);

        // then
        assertThat(response.inviteCode()).hasSize(6);
        assertThat(response.inviteCode()).matches("[A-Z0-9]{6}");
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("연동 관계가 없는 시니어의 코드 발급을 시도하면 CARE_RELATION_NOT_FOUND 에러")
    void createInviteCode_noRelation_throwsNotFound() {
        // given
        final User caregiver = mockUser(1L, UserRole.CAREGIVER);
        given(userRepository.findById(1L)).willReturn(Optional.of(caregiver));
        given(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(1L, 2L))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> careRelationService.createInviteCode(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    final BusinessException be = (BusinessException) exception;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.CARE_RELATION_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("유효한 초대 코드로 코드 로그인하면 JWT가 발급된다")
    void codeLogin_success() {
        // given
        final InviteCode.InviteCodeWithRaw inviteCodeWithRaw = InviteCode.create(2L, LocalDateTime.now());
        final String rawCode = inviteCodeWithRaw.rawCode();
        given(inviteCodeRepository.findAll()).willReturn(List.of(inviteCodeWithRaw.inviteCode()));
        given(jwtProvider.createAccessToken(2L)).willReturn("access-token");
        given(jwtProvider.createRefreshToken(2L)).willReturn("refresh-token");

        // when
        final LoginResponse response = careRelationService.codeLogin(rawCode, "127.0.0.1");

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(inviteCodeWithRaw.inviteCode().isUsed()).isTrue();
        verify(rateLimiter).clearFailures("code-login:127.0.0.1");
    }

    @Test
    @DisplayName("존재하지 않는 코드로 로그인 시도하면 CARE_RELATION_INVITE_INVALID 에러")
    void codeLogin_invalidCode_throws() {
        // given
        given(inviteCodeRepository.findAll()).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> careRelationService.codeLogin("BADCOD", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    final BusinessException be = (BusinessException) exception;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.CARE_RELATION_INVITE_INVALID);
                });
        verify(rateLimiter).recordFailure("code-login:127.0.0.1");
    }

    @Test
    @DisplayName("만료된 코드로 로그인 시도하면 CARE_RELATION_INVITE_INVALID 에러")
    void codeLogin_expiredCode_throws() {
        // given
        final InviteCode.InviteCodeWithRaw inviteCodeWithRaw = InviteCode.create(
                2L, LocalDateTime.now().minusMinutes(10));
        final String rawCode = inviteCodeWithRaw.rawCode();
        given(inviteCodeRepository.findAll()).willReturn(List.of(inviteCodeWithRaw.inviteCode()));

        // when & then
        assertThatThrownBy(() -> careRelationService.codeLogin(rawCode, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    final BusinessException be = (BusinessException) exception;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.CARE_RELATION_INVITE_INVALID);
                });
        verify(rateLimiter).recordFailure("code-login:127.0.0.1");
    }

    @Test
    @DisplayName("Rate Limit 초과 시 429 에러가 발생하고 downstream은 호출되지 않는다")
    void codeLogin_rateLimitExceeded_throws() {
        // given
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED))
                .when(rateLimiter).checkAllowed("code-login:127.0.0.1");

        // when & then
        assertThatThrownBy(() -> careRelationService.codeLogin("ABC123", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    final BusinessException be = (BusinessException) exception;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
                });
        verify(inviteCodeRepository, org.mockito.Mockito.never()).findAll();
    }

    private User mockUser(final Long id, final UserRole role) {
        final User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getRole()).thenReturn(role);
        return user;
    }
}
