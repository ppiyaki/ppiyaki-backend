package com.ppiyaki.user.controller;

import com.ppiyaki.user.controller.dto.CodeLoginRequest;
import com.ppiyaki.user.controller.dto.InviteCodeRequest;
import com.ppiyaki.user.controller.dto.InviteCodeResponse;
import com.ppiyaki.user.controller.dto.LoginResponse;
import com.ppiyaki.user.service.CareRelationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CareRelationController {

    private final CareRelationService careRelationService;

    public CareRelationController(final CareRelationService careRelationService) {
        this.careRelationService = careRelationService;
    }

    @PostMapping("/care-relations/invite")
    public ResponseEntity<InviteCodeResponse> createInviteCode(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final InviteCodeRequest inviteCodeRequest
    ) {
        final InviteCodeResponse inviteCodeResponse = careRelationService.createInviteCode(
                userId, inviteCodeRequest.seniorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(inviteCodeResponse);
    }

    @PostMapping("/auth/code-login")
    public ResponseEntity<LoginResponse> codeLogin(
            @Valid @RequestBody final CodeLoginRequest codeLoginRequest,
            final HttpServletRequest request
    ) {
        final String clientIp = request.getRemoteAddr();
        final LoginResponse loginResponse = careRelationService.codeLogin(
                codeLoginRequest.code(), clientIp);
        return ResponseEntity.ok(loginResponse);
    }
}
