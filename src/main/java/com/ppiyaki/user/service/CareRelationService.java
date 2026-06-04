package com.ppiyaki.user.service;

import com.ppiyaki.common.auth.JwtProvider;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.ratelimit.AttemptLimiter;
import com.ppiyaki.common.ratelimit.RateLimiter;
import com.ppiyaki.infrastructure.storage.ProfileImageUrlResolver;
import com.ppiyaki.user.controller.dto.CaregiverSummaryResponse;
import com.ppiyaki.user.controller.dto.InviteCodeResponse;
import com.ppiyaki.user.controller.dto.LoginResponse;
import com.ppiyaki.user.controller.dto.SeniorSummaryResponse;
import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.InviteCode;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.RefreshTokenRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareRelationService {

    private static final long INVITE_CODE_TTL_SECONDS = 300L;

    private final CareRelationRepository careRelationRepository;
    private final InviteCodeStore inviteCodeStore;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final AttemptLimiter attemptLimiter;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    public CareRelationService(
            final CareRelationRepository careRelationRepository,
            final InviteCodeStore inviteCodeStore,
            final RefreshTokenRepository refreshTokenRepository,
            final UserRepository userRepository,
            final JwtProvider jwtProvider,
            final AuthService authService,
            final RateLimiter rateLimiter,
            final AttemptLimiter attemptLimiter,
            final ProfileImageUrlResolver profileImageUrlResolver
    ) {
        this.careRelationRepository = careRelationRepository;
        this.inviteCodeStore = inviteCodeStore;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.attemptLimiter = attemptLimiter;
        this.profileImageUrlResolver = profileImageUrlResolver;
    }

    @Transactional(readOnly = true)
    public List<SeniorSummaryResponse> readSeniors(final Long caregiverId) {
        final List<CareRelation> careRelations = careRelationRepository.findByCaregiverIdAndDeletedAtIsNull(
                caregiverId);

        final List<Long> seniorIds = careRelations.stream()
                .map(CareRelation::getSeniorId)
                .toList();

        final Map<Long, User> seniorsById = userRepository.findAllById(seniorIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return careRelations.stream()
                .map(relation -> seniorsById.get(relation.getSeniorId()))
                .map(senior -> SeniorSummaryResponse.from(
                        senior, profileImageUrlResolver.resolve(senior.getProfileImageObjectKey())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CaregiverSummaryResponse> readCaregivers(final Long seniorId) {
        final List<CareRelation> careRelations = careRelationRepository.findBySeniorIdAndDeletedAtIsNull(
                seniorId);

        final List<Long> caregiverIds = careRelations.stream()
                .map(CareRelation::getCaregiverId)
                .toList();

        final Map<Long, User> caregiversById = userRepository.findAllById(caregiverIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return careRelations.stream()
                .map(relation -> caregiversById.get(relation.getCaregiverId()))
                .map(caregiver -> CaregiverSummaryResponse.from(
                        caregiver, profileImageUrlResolver.resolve(caregiver.getProfileImageObjectKey())))
                .toList();
    }

    public InviteCodeResponse createInviteCode(final Long caregiverId, final Long seniorId) {
        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(caregiverId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));

        final String rawCode = InviteCode.generateCode();
        final String codeHash = InviteCode.sha256(rawCode);
        inviteCodeStore.save(codeHash, seniorId, INVITE_CODE_TTL_SECONDS);

        final LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(INVITE_CODE_TTL_SECONDS);
        return new InviteCodeResponse(rawCode, expiresAt);
    }

    public LoginResponse codeLogin(final String code, final String clientIp) {
        final String rateLimitKey = "code-login:" + clientIp;
        rateLimiter.checkAllowed(rateLimitKey);

        final String codeHash = InviteCode.sha256(code);
        final String attemptKey = "code:" + codeHash;
        attemptLimiter.checkAllowed(attemptKey);

        final Long seniorId = inviteCodeStore.consume(codeHash)
                .orElse(null);

        if (seniorId == null) {
            rateLimiter.recordFailure(rateLimitKey);
            attemptLimiter.recordAttempt(attemptKey);
            throw new BusinessException(ErrorCode.CARE_RELATION_INVITE_INVALID);
        }

        rateLimiter.clearFailures(rateLimitKey);
        attemptLimiter.clear(attemptKey);

        final User senior = findUserById(seniorId);
        final String accessToken = jwtProvider.createAccessToken(seniorId, senior.getRole().name());
        final String refreshToken = jwtProvider.createRefreshToken(seniorId);
        authService.saveRefreshTokenForUser(seniorId, refreshToken);

        return new LoginResponse(accessToken, refreshToken, true);
    }

    @Transactional
    public void removeSeniorRelation(final Long caregiverId, final Long seniorId) {
        final CareRelation careRelation = careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(
                caregiverId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));

        careRelation.softDelete(LocalDateTime.now());
    }

    @Transactional
    public void forceLogoutSenior(final Long caregiverId, final Long seniorId) {
        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(caregiverId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));

        refreshTokenRepository.deleteByUserId(seniorId);
    }

    private User findUserById(final Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
