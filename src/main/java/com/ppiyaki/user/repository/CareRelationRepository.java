package com.ppiyaki.user.repository;

import com.ppiyaki.user.CareRelation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareRelationRepository extends JpaRepository<CareRelation, Long> {

    Optional<CareRelation> findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(
            Long caregiverId,
            Long seniorId
    );
}
