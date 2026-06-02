package com.ppiyaki.user.service;

import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.repository.PetRepository;
import com.ppiyaki.user.controller.dto.SeniorCreateRequest;
import com.ppiyaki.user.controller.dto.SeniorCreateResponse;
import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeniorService {

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;
    private final PetRepository petRepository;

    public SeniorService(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository,
            final PetRepository petRepository
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
        this.petRepository = petRepository;
    }

    @Transactional
    public SeniorCreateResponse createSenior(final Long caregiverId, final SeniorCreateRequest seniorCreateRequest) {
        final User senior = userRepository.save(
                User.createSenior(seniorCreateRequest.nickname(), seniorCreateRequest.gender()));
        senior.changeCareMode(seniorCreateRequest.careMode());

        final Pet pet = petRepository.save(Pet.create());
        senior.assignPet(pet.getId());

        final CareRelation careRelation = careRelationRepository.save(
                CareRelation.createLinked(senior.getId(), caregiverId));

        return new SeniorCreateResponse(senior.getId(), careRelation.getId(), pet.getId());
    }
}
