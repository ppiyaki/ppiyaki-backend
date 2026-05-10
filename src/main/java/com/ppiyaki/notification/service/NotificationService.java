package com.ppiyaki.notification.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.NotificationCategory;
import com.ppiyaki.notification.controller.dto.NotificationItemResponse;
import com.ppiyaki.notification.controller.dto.NotificationListResponse;
import com.ppiyaki.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;

    public NotificationService(final NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse listForUser(
            final Long userId,
            final NotificationCategory category,
            final Long cursor,
            final Integer size
    ) {
        final int pageSize = resolvePageSize(size);
        final List<Notification> page = notificationRepository.findPageByUserId(
                userId, category, cursor, PageRequest.of(0, pageSize + 1));

        final boolean hasNext = page.size() > pageSize;
        final List<Notification> items = hasNext ? page.subList(0, pageSize) : page;
        final Long nextCursor = hasNext ? items.get(items.size() - 1).getId() : null;

        final List<NotificationItemResponse> responses = items.stream()
                .map(NotificationItemResponse::from)
                .toList();

        return new NotificationListResponse(responses, nextCursor, hasNext);
    }

    @Transactional
    public void markAsRead(final Long userId, final Long notificationId) {
        final Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_FORBIDDEN);
        }
        notification.markAsRead(LocalDateTime.now());
    }

    @Transactional
    public void markAllAsRead(final Long userId) {
        notificationRepository.markAllAsRead(userId, LocalDateTime.now());
    }

    private int resolvePageSize(final Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
