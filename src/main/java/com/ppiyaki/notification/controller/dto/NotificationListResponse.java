package com.ppiyaki.notification.controller.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationItemResponse> responses,
        Long nextCursor,
        boolean hasNext
) {
}
