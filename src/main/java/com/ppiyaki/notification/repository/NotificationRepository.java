package com.ppiyaki.notification.repository;

import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.NotificationCategory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId
              AND (:category IS NULL OR n.category = :category)
              AND (:cursor IS NULL OR n.id < :cursor)
            ORDER BY n.id DESC
            """)
    List<Notification> findPageByUserId(
            @Param("userId") final Long userId,
            @Param("category") final NotificationCategory category,
            @Param("cursor") final Long cursor,
            final Pageable pageable
    );

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.readAt = :readAt
            WHERE n.userId = :userId AND n.readAt IS NULL
            """)
    int markAllAsRead(@Param("userId") final Long userId, @Param("readAt") final LocalDateTime readAt);

    boolean existsByUserIdAndCategoryAndTargetDateAndMealSlot(
            final Long userId,
            final NotificationCategory category,
            final java.time.LocalDate targetDate,
            final String mealSlot
    );

    boolean existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndScheduleId(
            final Long userId,
            final NotificationCategory category,
            final Long seniorId,
            final java.time.LocalDate targetDate,
            final Long scheduleId
    );

    java.util.Optional<Notification> findFirstByUserIdAndCategoryAndSeniorIdOrderByCreatedAtDesc(
            final Long userId,
            final NotificationCategory category,
            final Long seniorId
    );
}
