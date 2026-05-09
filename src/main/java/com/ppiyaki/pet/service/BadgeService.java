package com.ppiyaki.pet.service;

import com.ppiyaki.pet.Badge;
import com.ppiyaki.pet.BadgeType;
import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.repository.BadgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BadgeService {

    private static final int HEALTH_GUARDIAN_STREAK = 30;

    private final BadgeRepository badgeRepository;

    public BadgeService(final BadgeRepository badgeRepository) {
        this.badgeRepository = badgeRepository;
    }

    @Transactional
    public void checkAndAwardBadges(final Pet pet) {
        checkFirstStep(pet);
        checkHealthGuardian(pet);
    }

    private void checkFirstStep(final Pet pet) {
        if (pet.getPoint() > 0 && !hasBadge(pet.getId(), BadgeType.FIRST_STEP)) {
            badgeRepository.save(new Badge(pet.getId(), BadgeType.FIRST_STEP));
        }
    }

    private void checkHealthGuardian(final Pet pet) {
        if (pet.getCurrentStreak() >= HEALTH_GUARDIAN_STREAK
                && !hasBadge(pet.getId(), BadgeType.HEALTH_GUARDIAN)) {
            badgeRepository.save(new Badge(pet.getId(), BadgeType.HEALTH_GUARDIAN));
        }
    }

    private boolean hasBadge(final Long petId, final BadgeType badgeType) {
        return badgeRepository.existsByPetIdAndBadgeType(petId, badgeType);
    }
}
