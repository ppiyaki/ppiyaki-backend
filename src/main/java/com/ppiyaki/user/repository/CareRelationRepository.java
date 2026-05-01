package com.ppiyaki.user.repository;

import com.ppiyaki.user.CareRelation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareRelationRepository extends JpaRepository<CareRelation, Long> {

    Optional<CareRelation> findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(
            final Long caregiverId,
            final Long seniorId
    );

    Optional<CareRelation> findByInviteCodeAndSeniorIdIsNull(final String inviteCode);
}
