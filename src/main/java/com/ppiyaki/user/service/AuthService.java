package com.ppiyaki.user.service;

import com.ppiyaki.common.auth.JwtProperties;
import com.ppiyaki.common.auth.JwtProvider;
import com.ppiyaki.common.auth.KakaoIdTokenVerifier;
import com.ppiyaki.common.auth.KakaoIdTokenVerifier.KakaoIdTokenPayload;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.user.AuthProvider;
import com.ppiyaki.user.OAuthIdentity;
import com.ppiyaki.user.OAuthProvider;
import com.ppiyaki.user.RefreshToken;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.controller.dto.KakaoLoginRequest;
import com.ppiyaki.user.controller.dto.LoginRequest;
import com.ppiyaki.user.controller.dto.LoginResponse;
import com.ppiyaki.user.controller.dto.SignupRequest;
import com.ppiyaki.user.controller.dto.TokenResponse;
import com.ppiyaki.user.repository.OAuthIdentityRepository;
import com.ppiyaki.user.repository.RefreshTokenRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final KakaoIdTokenVerifier kakaoIdTokenVerifier;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final OAuthIdentityRepository oAuthIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthService(
            final KakaoIdTokenVerifier kakaoIdTokenVerifier,
            final JwtProvider jwtProvider,
            final JwtProperties jwtProperties,
            final PasswordEncoder passwordEncoder,
            final UserRepository userRepository,
            final OAuthIdentityRepository oAuthIdentityRepository,
            final RefreshTokenRepository refreshTokenRepository
    ) {
        this.kakaoIdTokenVerifier = kakaoIdTokenVerifier;
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.oAuthIdentityRepository = oAuthIdentityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public LoginResponse loginWithKakao(final KakaoLoginRequest kakaoLoginRequest) {
        final KakaoIdTokenPayload payload = kakaoIdTokenVerifier.verify(kakaoLoginRequest.idToken());

        final String providerUserId = payload.sub();
        final User user = oAuthIdentityRepository
                .findByProviderAndProviderUserId(OAuthProvider.KAKAO, providerUserId)
                .map(identity -> userRepository.findById(identity.getUserId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)))
                .orElseGet(() -> createNewUser(payload, providerUserId));

        final String accessToken = jwtProvider.createAccessToken(user.getId());
        final String refreshTokenValue = jwtProvider.createRefreshToken(user.getId());
        saveRefreshToken(user.getId(), refreshTokenValue);

        final boolean isOnboarded = user.getRole() != null;

        return new LoginResponse(accessToken, refreshTokenValue, isOnboarded);
    }

    @Transactional
    public LoginResponse signup(final SignupRequest signupRequest) {
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

        final String accessToken = jwtProvider.createAccessToken(user.getId());
        final String refreshTokenValue = jwtProvider.createRefreshToken(user.getId());
        saveRefreshToken(user.getId(), refreshTokenValue);

        return new LoginResponse(accessToken, refreshTokenValue, true);
    }

    @Transactional
    public LoginResponse login(final LoginRequest loginRequest) {
        final User user = userRepository.findByLoginId(loginRequest.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (user.getPassword() == null
                || !passwordEncoder.matches(loginRequest.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        final String accessToken = jwtProvider.createAccessToken(user.getId());
        final String refreshTokenValue = jwtProvider.createRefreshToken(user.getId());
        saveRefreshToken(user.getId(), refreshTokenValue);

        final boolean isOnboarded = user.getRole() != null;

        return new LoginResponse(accessToken, refreshTokenValue, isOnboarded);
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
        final RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        final Long userId = refreshToken.getUserId();
        final String newAccessToken = jwtProvider.createAccessToken(userId);
        final String newRefreshTokenValue = jwtProvider.createRefreshToken(userId);

        final LocalDateTime newExpiresAt = LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenExpiry() / 1000);
        refreshToken.rotate(newRefreshTokenValue, newExpiresAt);

        return new TokenResponse(newAccessToken, newRefreshTokenValue);
    }

    @Transactional
    public void logout(final String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(refreshTokenRepository::delete);
    }

    @Transactional(readOnly = true)
    public User findUserById(final Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void saveRefreshToken(final Long userId, final String tokenValue) {
        final LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenExpiry() / 1000);

        refreshTokenRepository.findByUserId(userId)
                .ifPresentOrElse(
                        existing -> existing.rotate(tokenValue, expiresAt),
                        () -> refreshTokenRepository.save(new RefreshToken(userId, tokenValue, expiresAt)));
    }
}
