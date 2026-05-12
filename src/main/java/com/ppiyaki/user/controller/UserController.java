package com.ppiyaki.user.controller;

import com.ppiyaki.user.controller.dto.CareModeResponse;
import com.ppiyaki.user.controller.dto.CareModeUpdateRequest;
import com.ppiyaki.user.controller.dto.MealTimesUpdateRequest;
import com.ppiyaki.user.controller.dto.UserMeResponse;
import com.ppiyaki.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PutMapping("/me/meal-times")
    public ResponseEntity<UserMeResponse> updateMealTimes(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final MealTimesUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateMealTimes(userId, request));
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal final Long userId) {
        userService.withdraw(userId);
    }
}
