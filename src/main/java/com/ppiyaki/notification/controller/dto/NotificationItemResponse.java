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
        String mealSlot,
        LocalDateTime readAt,
        LocalDateTime takenAt,
        LocalDateTime createdAt
) {

    public static NotificationItemResponse from(final Notification notification) {
        // dedup sentinel은 내부 저장용이므로 API 응답에서는 기존 계약대로 null로 되돌린다.
        final Long seniorId = notification.getSeniorId() != null
                && notification.getSeniorId() == Notification.SENTINEL_ID
                ? null : notification.getSeniorId();
        final String mealSlot = Notification.SENTINEL_MEAL_SLOT.equals(notification.getMealSlot())
                ? null : notification.getMealSlot();
        return new NotificationItemResponse(
                notification.getId(),
                notification.getCategory(),
                seniorId,
                notification.getTitle(),
                notification.getBody(),
                notification.getPayload(),
                mealSlot,
                notification.getReadAt(),
                notification.getTakenAt(),
                notification.getCreatedAt()
        );
    }
}
