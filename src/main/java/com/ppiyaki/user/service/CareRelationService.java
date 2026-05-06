package com.ppiyaki.user.service;

import com.ppiyaki.common.auth.JwtProvider;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.ratelimit.RateLimiter;
import com.ppiyaki.user.InviteCode;
import com.ppiyaki.user.InviteCode.InviteCodeWithRaw;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.controller.dto.InviteCodeResponse;
import com.ppiyaki.user.controller.dto.LoginResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.InviteCodeRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareRelationService {

    private final CareRelationRepository careRelationRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final AuthService authService;
    private final RateLimiter rateLimiter;

    public CareRelationService(
            final CareRelationRepository careRelationRepository,
            final InviteCodeRepository inviteCodeRepository,
            final UserRepository userRepository,
            final JwtProvider jwtProvider,
            final AuthService authService,
            final RateLimiter rateLimiter
    ) {
        this.careRelationRepository = careRelationRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public InviteCodeResponse createInviteCode(final Long caregiverId, final Long seniorId) {
        final User caregiver = findUserById(caregiverId);
        validateRole(caregiver, UserRole.CAREGIVER);

        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(caregiverId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));

        final InviteCodeWithRaw inviteCodeWithRaw = InviteCode.create(seniorId, LocalDateTime.now());
        inviteCodeRepository.save(inviteCodeWithRaw.inviteCode());

        return new InviteCodeResponse(inviteCodeWithRaw.rawCode(), inviteCodeWithRaw.inviteCode().getExpiresAt());
    }

    @Transactional
    public LoginResponse codeLogin(final String code, final String clientIp) {
        final String rateLimitKey = "code-login:" + clientIp;
        rateLimiter.checkAllowed(rateLimitKey);

        final List<InviteCode> candidates = inviteCodeRepository
                .findBySeniorIdAndUsedAtIsNullOrderByCreatedAtDesc(null);

        // 모든 미사용 초대 코드를 순회하며 hash 매칭
        final InviteCode matched = findMatchingInviteCode(code);
        if (matched == null) {
            rateLimiter.recordFailure(rateLimitKey);
            throw new BusinessException(ErrorCode.CARE_RELATION_INVITE_INVALID);
        }

        if (matched.isExpired(LocalDateTime.now())) {
            rateLimiter.recordFailure(rateLimitKey);
            throw new BusinessException(ErrorCode.CARE_RELATION_INVITE_INVALID);
        }

        matched.markUsed(LocalDateTime.now());
        rateLimiter.clearFailures(rateLimitKey);

        final Long seniorId = matched.getSeniorId();
        final String accessToken = jwtProvider.createAccessToken(seniorId);
        final String refreshToken = jwtProvider.createRefreshToken(seniorId);
        authService.saveRefreshTokenForUser(seniorId, refreshToken);

        return new LoginResponse(accessToken, refreshToken, true);
    }

    private InviteCode findMatchingInviteCode(final String rawCode) {
        final List<InviteCode> allUnused = inviteCodeRepository.findAll().stream()
                .filter(ic -> !ic.isUsed())
                .toList();

        for (final InviteCode inviteCode : allUnused) {
            if (inviteCode.matches(rawCode)) {
                return inviteCode;
            }
        }
        return null;
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
