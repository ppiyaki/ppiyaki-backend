package com.ppiyaki.pet.repository;

import com.ppiyaki.pet.Badge;
import com.ppiyaki.pet.BadgeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeRepository extends JpaRepository<Badge, Long> {

    List<Badge> findByPetId(final Long petId);

    boolean existsByPetIdAndBadgeType(final Long petId, final BadgeType badgeType);
}
