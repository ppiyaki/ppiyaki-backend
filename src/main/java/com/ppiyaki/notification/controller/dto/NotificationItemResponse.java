package com.ppiyaki.notification.controller.dto;

import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.NotificationCategory;
import java.time.LocalDateTime;

public record NotificationItemResponse(
        Long id,
        NotificationCategory category,
        Long seniorId,
        String title,
        String body,
        String payload,
        boolean isRead,
        LocalDateTime readAt,
        LocalDateTime takenAt,
        LocalDateTime createdAt
) {

    public static NotificationItemResponse from(final Notification notification) {
        return new NotificationItemResponse(
                notification.getId(),
                notification.getCategory(),
                notification.getSeniorId(),
                notification.getTitle(),
                notification.getBody(),
                notification.getPayload(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getTakenAt(),
                notification.getCreatedAt()
        );
    }
}
