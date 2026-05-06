package com.ppiyaki.user.repository;

import com.ppiyaki.user.SeniorDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeniorDeviceRepository extends JpaRepository<SeniorDevice, Long> {

    Optional<SeniorDevice> findBySeniorIdAndDeviceId(final Long seniorId, final String deviceId);

    List<SeniorDevice> findBySeniorIdAndStatus(
            final Long seniorId,
            final com.ppiyaki.user.SeniorDeviceStatus status
    );

    List<SeniorDevice> findBySeniorId(final Long seniorId);
}
