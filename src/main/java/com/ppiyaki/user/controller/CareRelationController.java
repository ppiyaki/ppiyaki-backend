package com.ppiyaki.user.controller;

import com.ppiyaki.user.controller.dto.AcceptInviteRequest;
import com.ppiyaki.user.controller.dto.AcceptInviteResponse;
import com.ppiyaki.user.controller.dto.InviteCodeResponse;
import com.ppiyaki.user.service.CareRelationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/care-relations")
public class CareRelationController {

    private final CareRelationService careRelationService;

    public CareRelationController(final CareRelationService careRelationService) {
        this.careRelationService = careRelationService;
    }

    @PostMapping("/invite")
    public ResponseEntity<InviteCodeResponse> createInviteCode(@AuthenticationPrincipal final Long userId) {
        final InviteCodeResponse inviteCodeResponse = careRelationService.createInviteCode(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(inviteCodeResponse);
    }

    @PostMapping("/accept")
    public ResponseEntity<AcceptInviteResponse> acceptInvite(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final AcceptInviteRequest acceptInviteRequest
    ) {
        final AcceptInviteResponse acceptInviteResponse = careRelationService.acceptInvite(
                userId, acceptInviteRequest.inviteCode());
        return ResponseEntity.ok(acceptInviteResponse);
    }
}
