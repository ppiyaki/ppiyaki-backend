package com.ppiyaki.user.service;

import com.ppiyaki.common.auth.JwtProperties;
import com.ppiyaki.common.auth.JwtProvider;
import com.ppiyaki.common.auth.KakaoOAuthClient;
import com.ppiyaki.common.auth.KakaoOAuthClient.KakaoUserInfo;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.user.OAuthIdentity;
import com.ppiyaki.user.OAuthProvider;
import com.ppiyaki.user.RefreshToken;
import com.ppiyaki.user.User;
import com.ppiyaki.user.controller.dto.KakaoLoginRequest;
import com.ppiyaki.user.controller.dto.LoginRequest;
import com.ppiyaki.user.controller.dto.LoginResponse;
import com.ppiyaki.user.controller.dto.SignupRequest;
import com.ppiyaki.user.controller.dto.TokenResponse;
import com.ppiyaki.user.repository.OAuthIdentityRepository;
import com.ppiyaki.user.repository.RefreshTokenRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final OAuthIdentityRepository oAuthIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthService(
            final KakaoOAuthClient kakaoOAuthClient,
            final JwtProvider jwtProvider,
            final JwtProperties jwtProperties,
            final PasswordEncoder passwordEncoder,
            final UserRepository userRepository,
            final OAuthIdentityRepository oAuthIdentityRepository,
            final RefreshTokenRepository refreshTokenRepository
    ) {
        this.kakaoOAuthClient = Objects.requireNonNull(kakaoOAuthClient, "kakaoOAuthClient must not be null");
        this.jwtProvider = Objects.requireNonNull(jwtProvider, "jwtProvider must not be null");
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.oAuthIdentityRepository = Objects.requireNonNull(oAuthIdentityRepository,
                "oAuthIdentityRepository must not be null");
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository,
                "refreshTokenRepository must not be null");
    }

    @Transactional
    public LoginResponse loginWithKakao(final KakaoLoginRequest kakaoLoginRequest) {
        Objects.requireNonNull(kakaoLoginRequest, "kakaoLoginRequest must not be null");

        final String kakaoAccessToken = kakaoOAuthClient.fetchAccessToken(kakaoLoginRequest.code(), kakaoLoginRequest
                .redirectUri());
        final KakaoUserInfo kakaoUserInfo = kakaoOAuthClient.fetchUserInfo(kakaoAccessToken);

        final String providerUserId = String.valueOf(kakaoUserInfo.id());
        final User user = oAuthIdentityRepository
                .findByProviderAndProviderUserId(OAuthProvider.KAKAO, providerUserId)
                .map(identity -> userRepository.findById(identity.getUserId()).orElseThrow())
                .orElseGet(() -> createNewUser(kakaoUserInfo, providerUserId));

        final String accessToken = jwtProvider.createAccessToken(user.getId());
        final String refreshTokenValue = jwtProvider.createRefreshToken(user.getId());
        saveRefreshToken(user.getId(), refreshTokenValue);

        final boolean isOnboarded = user.getRole() != null;

        return new LoginResponse(accessToken, refreshTokenValue, isOnboarded);
    }

    @Transactional
    public LoginResponse signup(final SignupRequest signupRequest) {
        Objects.requireNonNull(signupRequest, "signupRequest must not be null");

        if (userRepository.existsByLoginId(signupRequest.loginId())) {
            throw new BusinessException(ErrorCode.AUTH_DUPLICATE_LOGIN_ID);
        }

        final String encodedPassword = passwordEncoder.encode(signupRequest.password());
        final User user = userRepository.save(
                new User(signupRequest.loginId(), encodedPassword, null,
                        signupRequest.nickname(), null, null, null));

        final String accessToken = jwtProvider.createAccessToken(user.getId());
        final String refreshTokenValue = jwtProvider.createRefreshToken(user.getId());
        saveRefreshToken(user.getId(), refreshTokenValue);

        return new LoginResponse(accessToken, refreshTokenValue, false);
    }

    @Transactional
    public LoginResponse login(final LoginRequest loginRequest) {
        Objects.requireNonNull(loginRequest, "loginRequest must not be null");

        final User user = userRepository.findByLoginId(loginRequest.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

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

    private User createNewUser(final KakaoUserInfo kakaoUserInfo, final String providerUserId) {
        final User user = userRepository.save(
                new User(null, null, null, kakaoUserInfo.nickname(), null, null, null));

        oAuthIdentityRepository.save(new OAuthIdentity(user.getId(), OAuthProvider.KAKAO, providerUserId));

        return user;
    }

    @Transactional
    public TokenResponse refresh(final String refreshTokenValue) {
        Objects.requireNonNull(refreshTokenValue, "refreshTokenValue must not be null");

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
        Objects.requireNonNull(refreshTokenValue, "refreshTokenValue must not be null");
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(refreshTokenRepository::delete);
    }

    @Transactional(readOnly = true)
    public User findUserById(final Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found: " + userId));
    }

    private void saveRefreshToken(final Long userId, final String tokenValue) {
        final LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenExpiry() / 1000);

        refreshTokenRepository.findByUserId(userId)
                .ifPresentOrElse(
                        existing -> existing.rotate(tokenValue, expiresAt),
                        () -> refreshTokenRepository.save(new RefreshToken(userId, tokenValue, expiresAt)));
    }
}
