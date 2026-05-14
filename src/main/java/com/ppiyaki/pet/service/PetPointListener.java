package com.ppiyaki.pet.service;

import com.ppiyaki.medication.domain.LogStatus;
import com.ppiyaki.medication.domain.MedicationLog;
import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.repository.PetRepository;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PetPointListener {

    private static final Logger log = LoggerFactory.getLogger(PetPointListener.class);

    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final MedicationLogRepository medicationLogRepository;
    private final BadgeService badgeService;
    private final long pointPerTaken;

    public PetPointListener(
            final UserRepository userRepository,
            final PetRepository petRepository,
            final MedicationScheduleRepository medicationScheduleRepository,
            final MedicationLogRepository medicationLogRepository,
            final BadgeService badgeService,
            @Value("${pet.points.per-taken:10}") final long pointPerTaken
    ) {
        this.userRepository = userRepository;
        this.petRepository = petRepository;
        this.medicationScheduleRepository = medicationScheduleRepository;
        this.medicationLogRepository = medicationLogRepository;
        this.badgeService = badgeService;
        this.pointPerTaken = pointPerTaken;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMedicationTaken(final MedicationTakenEvent event) {
        final User user = userRepository.findById(event.seniorId()).orElse(null);
        if (user == null || user.getPet() == null) {
            log.debug("Pet not linked for seniorId={}, skipping point", event.seniorId());
            return;
        }

        final Pet pet = petRepository.findById(user.getPet()).orElse(null);
        if (pet == null) {
            log.debug("Pet entity not found for petId={}, skipping point", user.getPet());
            return;
        }

        pet.addPoint(pointPerTaken);

        final LocalDate targetDate = event.targetDate();
        if (isDayFullyTaken(event.seniorId(), targetDate)) {
            pet.incrementStreak(targetDate);
        }

        badgeService.checkAndAwardBadges(pet);
    }

    private boolean isDayFullyTaken(final Long seniorId, final LocalDate date) {
        final int totalSchedules = medicationScheduleRepository
                .findActiveByOwnerAndDate(seniorId, date).size();
        if (totalSchedules == 0) {
            return false;
        }

        final List<MedicationLog> logs = medicationLogRepository
                .findBySeniorIdAndTargetDate(seniorId, date);
        final long takenCount = logs.stream()
                .filter(medicationLog -> medicationLog.getStatus() == LogStatus.TAKEN)
                .count();

        return takenCount == totalSchedules;
    }
}
