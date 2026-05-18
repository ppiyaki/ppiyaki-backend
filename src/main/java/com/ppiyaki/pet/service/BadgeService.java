package com.ppiyaki.pet.service;

import com.ppiyaki.pet.Badge;
import com.ppiyaki.pet.BadgeType;
import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.repository.BadgeRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BadgeService {

    private static final Logger log = LoggerFactory.getLogger(BadgeService.class);
    private static final int HEALTH_GUARDIAN_STREAK = 30;
    private static final String METRIC = "ppiyaki.badge.awarded.total";

    private final BadgeRepository badgeRepository;
    private final MeterRegistry meterRegistry;

    public BadgeService(final BadgeRepository badgeRepository, final MeterRegistry meterRegistry) {
        this.badgeRepository = badgeRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void checkAndAwardBadges(final Pet pet) {
        checkFirstStep(pet);
        checkHealthGuardian(pet);
    }

    private void checkFirstStep(final Pet pet) {
        if (pet.getPoint() > 0) {
            saveIfAbsent(pet.getId(), BadgeType.FIRST_STEP);
        }
    }

    private void checkHealthGuardian(final Pet pet) {
        if (pet.getStreak() >= HEALTH_GUARDIAN_STREAK) {
            saveIfAbsent(pet.getId(), BadgeType.HEALTH_GUARDIAN);
        }
    }

    private void saveIfAbsent(final Long petId, final BadgeType badgeType) {
        try {
            badgeRepository.save(new Badge(petId, badgeType));
            badgeRepository.flush();
            meterRegistry.counter(METRIC, "badge_type", badgeType.name()).increment();
        } catch (final DataIntegrityViolationException e) {
            log.debug("Badge already exists: petId={}, type={}", petId, badgeType);
        }
    }
}
