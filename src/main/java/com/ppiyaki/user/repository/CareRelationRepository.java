package com.ppiyaki.user.repository;

import com.ppiyaki.user.CareRelation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareRelationRepository extends JpaRepository<CareRelation, Long> {

    Optional<CareRelation> findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(
            final Long caregiverId,
            final Long seniorId
    );

    List<CareRelation> findBySeniorIdAndDeletedAtIsNull(final Long seniorId);
}
