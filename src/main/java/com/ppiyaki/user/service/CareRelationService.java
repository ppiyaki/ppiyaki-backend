package com.ppiyaki.user.service;

import com.ppiyaki.common.auth.JwtProvider;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.ratelimit.RateLimiter;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.InviteCode;
import com.ppiyaki.user.InviteCode.InviteCodeWithRaw;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.controller.dto.InviteCodeResponse;
import com.ppiyaki.user.controller.dto.LoginResponse;
import com.ppiyaki.user.controller.dto.SeniorSummaryResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.InviteCodeRepository;
import com.ppiyaki.user.repository.RefreshTokenRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareRelationService {

    private final CareRelationRepository careRelationRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final AuthService authService;
    private final RateLimiter rateLimiter;

    public CareRelationService(
            final CareRelationRepository careRelationRepository,
            final InviteCodeRepository inviteCodeRepository,
            final RefreshTokenRepository refreshTokenRepository,
            final UserRepository userRepository,
            final JwtProvider jwtProvider,
            final AuthService authService,
            final RateLimiter rateLimiter
    ) {
        this.careRelationRepository = careRelationRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public List<SeniorSummaryResponse> readSeniors(final Long caregiverId) {
        final User caregiver = findUserById(caregiverId);
        validateRole(caregiver, UserRole.CAREGIVER);

        final List<CareRelation> careRelations = careRelationRepository.findByCaregiverIdAndDeletedAtIsNull(
                caregiverId);

        return careRelations.stream()
                .map(relation -> findUserById(relation.getSeniorId()))
                .map(SeniorSummaryResponse::from)
                .toList();
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

        final String codeHash = InviteCode.sha256(code);
        final InviteCode inviteCode = inviteCodeRepository.findByCodeHashAndUsedAtIsNull(codeHash)
                .orElse(null);

        if (inviteCode == null) {
            rateLimiter.recordFailure(rateLimitKey);
            throw new BusinessException(ErrorCode.CARE_RELATION_INVITE_INVALID);
        }

        final LocalDateTime now = LocalDateTime.now();
        if (inviteCode.isExpired(now)) {
            rateLimiter.recordFailure(rateLimitKey);
            throw new BusinessException(ErrorCode.CARE_RELATION_INVITE_INVALID);
        }

        inviteCode.markUsed(now);
        rateLimiter.clearFailures(rateLimitKey);

        final Long seniorId = inviteCode.getSeniorId();
        final User senior = findUserById(seniorId);
        final String accessToken = jwtProvider.createAccessToken(seniorId, senior.getRole().name());
        final String refreshToken = jwtProvider.createRefreshToken(seniorId);
        authService.saveRefreshTokenForUser(seniorId, refreshToken);

        return new LoginResponse(accessToken, refreshToken, true);
    }

    @Transactional
    public void forceLogoutSenior(final Long caregiverId, final Long seniorId) {
        final User caregiver = findUserById(caregiverId);
        validateRole(caregiver, UserRole.CAREGIVER);

        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(caregiverId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));

        refreshTokenRepository.deleteByUserId(seniorId);
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
