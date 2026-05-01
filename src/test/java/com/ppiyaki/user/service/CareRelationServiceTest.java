package com.ppiyaki.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.controller.dto.AcceptInviteResponse;
import com.ppiyaki.user.controller.dto.InviteCodeResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDateTime;
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
    private UserRepository userRepository;

    @InjectMocks
    private CareRelationService careRelationService;

    @Test
    @DisplayName("보호자가 초대 코드를 발급하면 6자리 코드와 만료 시각을 반환한다")
    void createInviteCode_caregiver_returnsCodeAndExpiry() {
        // given
        final User caregiver = mockUser(1L, UserRole.CAREGIVER);
        given(userRepository.findById(1L)).willReturn(Optional.of(caregiver));
        given(careRelationRepository.save(any(CareRelation.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        final InviteCodeResponse inviteCodeResponse = careRelationService.createInviteCode(1L);

        // then
        assertThat(inviteCodeResponse.inviteCode()).hasSize(6);
        assertThat(inviteCodeResponse.inviteCode()).matches("[A-Z0-9]{6}");
        assertThat(inviteCodeResponse.expiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("시니어가 초대 코드 발급을 시도하면 CARE_RELATION_ROLE_MISMATCH 에러가 발생한다")
    void createInviteCode_senior_throwsRoleMismatch() {
        // given
        final User senior = mockUser(1L, UserRole.SENIOR);
        given(userRepository.findById(1L)).willReturn(Optional.of(senior));

        // when & then
        assertThatThrownBy(() -> careRelationService.createInviteCode(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    final BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CARE_RELATION_ROLE_MISMATCH);
                });
    }

    @Test
    @DisplayName("시니어가 유효한 초대 코드로 연동을 수락하면 연동 정보를 반환한다")
    void acceptInvite_validCode_returnsRelation() {
        // given
        final User senior = mockUser(2L, UserRole.SENIOR);
        given(userRepository.findById(2L)).willReturn(Optional.of(senior));

        final CareRelation pendingRelation = CareRelation.createInvite(1L, LocalDateTime.now());
        final String inviteCode = pendingRelation.getInviteCode();
        given(careRelationRepository.findByInviteCodeAndSeniorIdIsNull(inviteCode))
                .willReturn(Optional.of(pendingRelation));
        given(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(any(), any()))
                .willReturn(Optional.empty());

        // when
        final AcceptInviteResponse acceptInviteResponse = careRelationService.acceptInvite(2L, inviteCode);

        // then
        assertThat(acceptInviteResponse.caregiverId()).isEqualTo(1L);
        assertThat(acceptInviteResponse.seniorId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("존재하지 않는 초대 코드로 연동을 시도하면 CARE_RELATION_INVITE_NOT_FOUND 에러가 발생한다")
    void acceptInvite_invalidCode_throwsNotFound() {
        // given
        final User senior = mockUser(2L, UserRole.SENIOR);
        given(userRepository.findById(2L)).willReturn(Optional.of(senior));
        given(careRelationRepository.findByInviteCodeAndSeniorIdIsNull("BADCOD"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> careRelationService.acceptInvite(2L, "BADCOD"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    final BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CARE_RELATION_INVITE_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("보호자가 초대 코드 수락을 시도하면 CARE_RELATION_ROLE_MISMATCH 에러가 발생한다")
    void acceptInvite_caregiver_throwsRoleMismatch() {
        // given
        final User caregiver = mockUser(1L, UserRole.CAREGIVER);
        given(userRepository.findById(1L)).willReturn(Optional.of(caregiver));

        // when & then
        assertThatThrownBy(() -> careRelationService.acceptInvite(1L, "ABC123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    final BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CARE_RELATION_ROLE_MISMATCH);
                });
    }

    @Test
    @DisplayName("이미 연동된 보호자-시니어 쌍이면 CARE_RELATION_ALREADY_EXISTS 에러가 발생한다")
    void acceptInvite_duplicateRelation_throwsAlreadyExists() {
        // given
        final User senior = mockUser(2L, UserRole.SENIOR);
        given(userRepository.findById(2L)).willReturn(Optional.of(senior));

        final CareRelation pendingRelation = CareRelation.createInvite(1L, LocalDateTime.now());
        final String inviteCode = pendingRelation.getInviteCode();
        given(careRelationRepository.findByInviteCodeAndSeniorIdIsNull(inviteCode))
                .willReturn(Optional.of(pendingRelation));

        final CareRelation existingRelation = new CareRelation(2L, 1L, "OLD");
        given(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(any(), any()))
                .willReturn(Optional.of(existingRelation));

        // when & then
        assertThatThrownBy(() -> careRelationService.acceptInvite(2L, inviteCode))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    final BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CARE_RELATION_ALREADY_EXISTS);
                });
    }

    private User mockUser(final Long id, final UserRole role) {
        final User user = mock(User.class);
        org.mockito.Mockito.lenient().when(user.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(user.getRole()).thenReturn(role);
        return user;
    }
}
