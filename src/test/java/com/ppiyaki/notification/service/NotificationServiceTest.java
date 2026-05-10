package com.ppiyaki.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.controller.dto.NotificationListResponse;
import com.ppiyaki.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("repo가 pageSize+1개 반환하면 hasNext=true + nextCursor는 마지막 표시 row id")
    void listForUser_hasNext() {
        // given — pageSize=2, repo returns 3
        final Notification first = mock(Notification.class);
        final Notification second = mock(Notification.class);
        given(second.getId()).willReturn(100L);
        final Notification third = mock(Notification.class);
        given(notificationRepository.findPageByUserId(any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of(first, second, third));

        // when
        final NotificationListResponse response = notificationService.listForUser(7L, null, null, 2);

        // then
        assertThat(response.responses()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(100L);
    }

    @Test
    @DisplayName("repo가 pageSize 이하 반환하면 hasNext=false + nextCursor=null")
    void listForUser_lastPage() {
        // given
        final Notification only = mock(Notification.class);
        given(notificationRepository.findPageByUserId(any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of(only));

        // when
        final NotificationListResponse response = notificationService.listForUser(7L, null, null, 20);

        // then
        assertThat(response.responses()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("본인 알림 markAsRead 시 entity.markAsRead 호출")
    void markAsRead_owner_success() {
        // given
        final Notification notification = mock(Notification.class);
        given(notification.getUserId()).willReturn(7L);
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        // when
        notificationService.markAsRead(7L, 1L);

        // then
        verify(notification).markAsRead(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("타인 알림 markAsRead 시 NOTIFICATION_FORBIDDEN")
    void markAsRead_nonOwner_throwsForbidden() {
        // given — notification.userId=7, requester=99
        final Notification notification = mock(Notification.class);
        given(notification.getUserId()).willReturn(7L);
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        // when & then
        assertThatThrownBy(() -> notificationService.markAsRead(99L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.NOTIFICATION_FORBIDDEN));
    }

    @Test
    @DisplayName("미존재 알림 markAsRead 시 NOTIFICATION_NOT_FOUND")
    void markAsRead_notFound() {
        // given
        given(notificationRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationService.markAsRead(7L, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @Test
    @DisplayName("markAllAsRead는 repository.markAllAsRead 호출")
    void markAllAsRead_callsRepository() {
        // when
        notificationService.markAllAsRead(7L);

        // then
        verify(notificationRepository).markAllAsRead(anyLong(), any(LocalDateTime.class));
    }
}
