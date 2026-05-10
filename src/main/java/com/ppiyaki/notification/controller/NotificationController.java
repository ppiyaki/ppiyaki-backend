package com.ppiyaki.notification.controller;

import com.ppiyaki.notification.NotificationCategory;
import com.ppiyaki.notification.controller.dto.NotificationListResponse;
import com.ppiyaki.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(final NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<NotificationListResponse> list(
            @AuthenticationPrincipal final Long userId,
            @RequestParam(required = false) final NotificationCategory category,
            @RequestParam(required = false) final Long cursor,
            @RequestParam(required = false) final Integer size
    ) {
        return ResponseEntity.ok(notificationService.listForUser(userId, category, cursor, size));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long notificationId
    ) {
        notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal final Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }
}
