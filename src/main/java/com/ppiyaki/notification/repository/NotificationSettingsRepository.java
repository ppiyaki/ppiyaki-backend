package com.ppiyaki.notification.repository;

import com.ppiyaki.notification.NotificationSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {

    Optional<NotificationSettings> findByCaregiverIdAndSeniorId(final Long caregiverId, final Long seniorId);
}
