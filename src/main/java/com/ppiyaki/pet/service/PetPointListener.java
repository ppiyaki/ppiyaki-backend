package com.ppiyaki.pet.service;

import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.repository.PetRepository;
import com.ppiyaki.user.User;
import com.ppiyaki.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PetPointListener {

    private static final long POINT_PER_TAKEN = 10L;
    private static final Logger log = LoggerFactory.getLogger(PetPointListener.class);

    private final UserRepository userRepository;
    private final PetRepository petRepository;

    public PetPointListener(
            final UserRepository userRepository,
            final PetRepository petRepository
    ) {
        this.userRepository = userRepository;
        this.petRepository = petRepository;
    }

    @EventListener
    @Transactional
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

        pet.addPoint(POINT_PER_TAKEN);
    }
}
