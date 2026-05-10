package com.ppiyaki.notification.repository;

import com.ppiyaki.notification.DeviceToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(final String token);

    List<DeviceToken> findByUserIdAndIsActiveTrue(final Long userId);
}
