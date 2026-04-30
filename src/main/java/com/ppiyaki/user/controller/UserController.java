package com.ppiyaki.user.controller;

import com.ppiyaki.user.controller.dto.CareModeResponse;
import com.ppiyaki.user.controller.dto.CareModeUpdateRequest;
import com.ppiyaki.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(final UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/{seniorId}/care-mode")
    public ResponseEntity<CareModeResponse> updateCareMode(
            @AuthenticationPrincipal final Long requesterId,
            @PathVariable final Long seniorId,
            @Valid @RequestBody final CareModeUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateCareMode(requesterId, seniorId, request.careMode()));
    }
}
