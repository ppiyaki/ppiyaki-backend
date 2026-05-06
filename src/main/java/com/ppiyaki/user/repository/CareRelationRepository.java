package com.ppiyaki.user.repository;

import com.ppiyaki.user.CareRelation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CareRelationRepository extends JpaRepository<CareRelation, Long> {

    Optional<CareRelation> findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(
            final Long caregiverId,
            final Long seniorId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CareRelation> findByInviteCodeAndDeletedAtIsNull(final String inviteCode);
}
