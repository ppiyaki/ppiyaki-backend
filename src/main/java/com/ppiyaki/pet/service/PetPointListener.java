package com.ppiyaki.pet.service;

import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.repository.PetRepository;
import com.ppiyaki.user.User;
import com.ppiyaki.user.repository.UserRepository;
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
    private final long pointPerTaken;

    public PetPointListener(
            final UserRepository userRepository,
            final PetRepository petRepository,
            @Value("${pet.points.per-taken:10}") final long pointPerTaken
    ) {
        this.userRepository = userRepository;
        this.petRepository = petRepository;
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
    }
}
