package com.ppiyaki.user.service;

import com.ppiyaki.common.auth.JwtProperties;
import com.ppiyaki.common.auth.JwtProvider;
import com.ppiyaki.common.auth.KakaoIdTokenVerifier;
import com.ppiyaki.common.auth.KakaoIdTokenVerifier.KakaoIdTokenPayload;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.user.controller.dto.KakaoLoginRequest;
import com.ppiyaki.user.controller.dto.LoginRequest;
import com.ppiyaki.user.controller.dto.LoginResponse;
import com.ppiyaki.user.controller.dto.SignupRequest;
import com.ppiyaki.user.controller.dto.TokenResponse;
import com.ppiyaki.user.domain.AuthProvider;
import com.ppiyaki.user.domain.OAuthIdentity;
import com.ppiyaki.user.domain.OAuthProvider;
import com.ppiyaki.user.domain.RefreshToken;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.domain.UserRole;
import com.ppiyaki.user.repository.OAuthIdentityRepository;
import com.ppiyaki.user.repository.RefreshTokenRepository;
import com.ppiyaki.user.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String METRIC_ATTEMPTS = "ppiyaki.auth.attempts.total";
    private static final String TYPE_KAKAO = "kakao";
    private static final String TYPE_LOCAL = "local";
    private static final String ACTION_LOGIN = "login";
    private static final String ACTION_SIGNUP = "signup";
    private static final String ACTION_REFRESH = "refresh";
    private static final String ACTION_LOGOUT = "logout";

    private final KakaoIdTokenVerifier kakaoIdTokenVerifier;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final OAuthIdentityRepository oAuthIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MeterRegistry meterRegistry;

    public AuthService(
            final KakaoIdTokenVerifier kakaoIdTokenVerifier,
            final JwtProvider jwtProvider,
            final JwtProperties jwtProperties,
            final PasswordEncoder passwordEncoder,
            final UserRepository userRepository,
            final OAuthIdentityRepository oAuthIdentityRepository,
            final RefreshTokenRepository refreshTokenRepository,
            final MeterRegistry meterRegistry
    ) {
        this.kakaoIdTokenVerifier = kakaoIdTokenVerifier;
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.oAuthIdentityRepository = oAuthIdentityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public LoginResponse loginWithKakao(final KakaoLoginRequest kakaoLoginRequest) {
        try {
            final KakaoIdTokenPayload payload = kakaoIdTokenVerifier.verify(kakaoLoginRequest.idToken());

            final String providerUserId = payload.sub();
            final User user = oAuthIdentityRepository
                    .findByProviderAndProviderUserId(OAuthProvider.KAKAO, providerUserId)
                    .map(identity -> userRepository.findById(identity.getUserId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)))
                    .orElseGet(() -> createNewUser(payload, providerUserId));

            if (user.isDeleted()) {
                throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);
            }

            final String roleName = user.getRole() != null ? user.getRole().name() : null;
            final String accessToken = jwtProvider.createAccessToken(user.getId(), roleName);
            final String refreshTokenValue = jwtProvider.createRefreshToken(user.getId());
            saveRefreshToken(user.getId(), refreshTokenValue);

            final boolean isOnboarded = user.isOnboarded();

            recordAttempt(TYPE_KAKAO, ACTION_LOGIN, "success");
            return new LoginResponse(accessToken, refreshTokenValue, isOnboarded);
        } catch (final BusinessException e) {
            recordAttempt(TYPE_KAKAO, ACTION_LOGIN, mapResult(e));
            throw e;
        } catch (final RuntimeException e) {
            recordAttempt(TYPE_KAKAO, ACTION_LOGIN, "failed");
            throw e;
        }
    }

    @Transactional
    public LoginResponse signup(final SignupRequest signupRequest) {
        try {
            if (userRepository.existsByLoginId(signupRequest.loginId())) {
                throw new BusinessException(ErrorCode.AUTH_DUPLICATE_LOGIN_ID);
            }

            final String encodedPassword = passwordEncoder.encode(signupRequest.password());
            final User user;
            try {
                user = userRepository.save(
                        new User(signupRequest.loginId(), encodedPassword, UserRole.CAREGIVER,
                                AuthProvider.LOCAL, signupRequest.nickname(), null, null, null));
            } catch (final DataIntegrityViolationException e) {
                throw new BusinessException(ErrorCode.AUTH_DUPLICATE_LOGIN_ID);
            }

            final String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole().name());
            final String refreshTokenValue = jwtProvider.createRefreshToken(user.getId());
            saveRefreshToken(user.getId(), refreshTokenValue);

            recordAttempt(TYPE_LOCAL, ACTION_SIGNUP, "success");
            return new LoginResponse(accessToken, refreshTokenValue, user.isOnboarded());
        } catch (final BusinessException e) {
            recordAttempt(TYPE_LOCAL, ACTION_SIGNUP, mapResult(e));
            throw e;
        } catch (final RuntimeException e) {
            recordAttempt(TYPE_LOCAL, ACTION_SIGNUP, "failed");
            throw e;
        }
    }

    @Transactional
    public LoginResponse login(final LoginRequest loginRequest) {
        try {
            final User user = userRepository.findByLoginId(loginRequest.loginId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

            if (user.getAuthProvider() != AuthProvider.LOCAL) {
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
            }

            if (user.getPassword() == null
                    || !passwordEncoder.matches(loginRequest.password(), user.getPassword())) {
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
            }

            if (user.isDeleted()) {
                throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);
            }

            final String roleName = user.getRole() != null ? user.getRole().name() : null;
            final String accessToken = jwtProvider.createAccessToken(user.getId(), roleName);
            final String refreshTokenValue = jwtProvider.createRefreshToken(user.getId());
            saveRefreshToken(user.getId(), refreshTokenValue);

            final boolean isOnboarded = user.isOnboarded();

            recordAttempt(TYPE_LOCAL, ACTION_LOGIN, "success");
            return new LoginResponse(accessToken, refreshTokenValue, isOnboarded);
        } catch (final BusinessException e) {
            recordAttempt(TYPE_LOCAL, ACTION_LOGIN, mapResult(e));
            throw e;
        } catch (final RuntimeException e) {
            recordAttempt(TYPE_LOCAL, ACTION_LOGIN, "failed");
            throw e;
        }
    }

    private User createNewUser(final KakaoIdTokenPayload payload, final String providerUserId) {
        final User user = userRepository.save(
                new User(null, null, UserRole.CAREGIVER, AuthProvider.KAKAO,
                        payload.nickname(), null, null, null));

        oAuthIdentityRepository.save(new OAuthIdentity(user.getId(), OAuthProvider.KAKAO, providerUserId));

        return user;
    }

    @Transactional
    public TokenResponse refresh(final String refreshTokenValue) {
        try {
            final RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

            if (refreshToken.isExpired()) {
                refreshTokenRepository.delete(refreshToken);
                throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
            }

            final Long userId = refreshToken.getUserId();
            final User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            final String newAccessToken = jwtProvider.createAccessToken(userId, user.getRole().name());
            final String newRefreshTokenValue = jwtProvider.createRefreshToken(userId);

            final LocalDateTime newExpiresAt = LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenExpiry()
                    / 1000);
            refreshToken.rotate(newRefreshTokenValue, newExpiresAt);

            recordAttempt("unknown", ACTION_REFRESH, "success");
            return new TokenResponse(newAccessToken, newRefreshTokenValue);
        } catch (final BusinessException e) {
            recordAttempt("unknown", ACTION_REFRESH, mapResult(e));
            throw e;
        } catch (final RuntimeException e) {
            recordAttempt("unknown", ACTION_REFRESH, "failed");
            throw e;
        }
    }

    @Transactional
    public void logout(final String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(refreshTokenRepository::delete);
        recordAttempt("unknown", ACTION_LOGOUT, "success");
    }

    private void recordAttempt(final String type, final String action, final String result) {
        meterRegistry.counter(METRIC_ATTEMPTS,
                "type", type, "action", action, "result", result).increment();
    }

    private static String mapResult(final BusinessException exception) {
        return switch (exception.getErrorCode()) {
            case AUTH_DUPLICATE_LOGIN_ID -> "duplicate_id";
            case AUTH_INVALID_CREDENTIALS -> "invalid_credentials";
            case AUTH_INVALID_TOKEN -> "invalid_token";
            case AUTH_TOKEN_EXPIRED -> "token_expired";
            case USER_ALREADY_DELETED -> "user_deleted";
            default -> "failed";
        };
    }

    @Transactional(readOnly = true)
    public User findUserById(final Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public void saveRefreshTokenForUser(final Long userId, final String tokenValue) {
        saveRefreshToken(userId, tokenValue);
    }

    private void saveRefreshToken(final Long userId, final String tokenValue) {
        final LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenExpiry() / 1000);

        refreshTokenRepository.findByUserId(userId)
                .ifPresentOrElse(
                        existing -> existing.rotate(tokenValue, expiresAt),
                        () -> refreshTokenRepository.save(new RefreshToken(userId, tokenValue, expiresAt)));
    }
}
