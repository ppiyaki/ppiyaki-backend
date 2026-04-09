package com.ppiyaki.user.controller;

import com.ppiyaki.user.User;
import com.ppiyaki.user.controller.dto.KakaoLoginRequest;
import com.ppiyaki.user.controller.dto.LoginResponse;
import com.ppiyaki.user.controller.dto.LogoutRequest;
import com.ppiyaki.user.controller.dto.RefreshRequest;
import com.ppiyaki.user.controller.dto.TokenResponse;
import com.ppiyaki.user.controller.dto.UserMeResponse;
import com.ppiyaki.user.service.AuthService;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;

    public AuthController(final AuthService authService) {
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
    }

    @PostMapping("/auth/kakao")
    public ResponseEntity<LoginResponse> loginWithKakao(@RequestBody final KakaoLoginRequest kakaoLoginRequest) {
        final LoginResponse loginResponse = authService.loginWithKakao(kakaoLoginRequest);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody final RefreshRequest refreshRequest) {
        final TokenResponse tokenResponse = authService.refresh(refreshRequest.refreshToken());
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody final LogoutRequest logoutRequest) {
        authService.logout(logoutRequest.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me")
    public ResponseEntity<UserMeResponse> me(@AuthenticationPrincipal final Long userId) {
        final User user = authService.findUserById(userId);
        final UserMeResponse userMeResponse = UserMeResponse.from(user);
        return ResponseEntity.ok(userMeResponse);
    }
}
