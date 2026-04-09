package com.ppiyaki.user.controller;

import com.ppiyaki.user.controller.dto.KakaoLoginRequest;
import com.ppiyaki.user.controller.dto.LoginResponse;
import com.ppiyaki.user.service.AuthService;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(final AuthService authService) {
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
    }

    @PostMapping("/kakao")
    public ResponseEntity<LoginResponse> loginWithKakao(@RequestBody final KakaoLoginRequest kakaoLoginRequest) {
        final LoginResponse loginResponse = authService.loginWithKakao(kakaoLoginRequest);
        return ResponseEntity.ok(loginResponse);
    }
}
