package com.ppiyaki.user.service;

import com.ppiyaki.common.auth.JwtProvider;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.controller.dto.InviteCodeResponse;
import com.ppiyaki.user.controller.dto.LoginResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareRelationService {

    private static final int MAX_INVITE_CODE_RETRIES = 3;

    private final CareRelationRepository careRelationRepository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final AuthService authService;

    public CareRelationService(
            final CareRelationRepository careRelationRepository,
            final UserRepository userRepository,
            final JwtProvider jwtProvider,
            final AuthService authService
    ) {
        this.careRelationRepository = careRelationRepository;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.authService = authService;
    }

    @Transactional
    public InviteCodeResponse createInviteCode(final Long caregiverId, final Long seniorId) {
        final User caregiver = findUserById(caregiverId);
        validateRole(caregiver, UserRole.CAREGIVER);

        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(caregiverId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));

        for (int attempt = 0; attempt < MAX_INVITE_CODE_RETRIES; attempt++) {
            final CareRelation inviteRelation = CareRelation.createInviteForSenior(
                    seniorId, caregiverId, LocalDateTime.now());
            try {
                careRelationRepository.saveAndFlush(inviteRelation);
                return new InviteCodeResponse(inviteRelation.getInviteCode(), inviteRelation.getExpiresAt());
            } catch (final DataIntegrityViolationException ignored) {
                // invite_code 중복 — 재생성 시도
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to generate unique invite code");
    }

    @Transactional
    public LoginResponse codeLogin(final String code) {
        final CareRelation careRelation = careRelationRepository
                .findByInviteCodeAndDeletedAtIsNull(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_INVITE_INVALID));

        if (careRelation.isExpired(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CARE_RELATION_INVITE_INVALID);
        }

        final Long seniorId = careRelation.getSeniorId();
        careRelation.consumeInviteCode();

        final String accessToken = jwtProvider.createAccessToken(seniorId);
        final String refreshToken = jwtProvider.createRefreshToken(seniorId);
        authService.saveRefreshTokenForUser(seniorId, refreshToken);

        return new LoginResponse(accessToken, refreshToken, true);
    }

    private User findUserById(final Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateRole(final User user, final UserRole expectedRole) {
        if (user.getRole() != expectedRole) {
            throw new BusinessException(ErrorCode.CARE_RELATION_ROLE_MISMATCH);
        }
    }
}
