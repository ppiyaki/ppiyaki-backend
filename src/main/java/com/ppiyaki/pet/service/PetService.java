package com.ppiyaki.pet.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.controller.dto.PetResponse;
import com.ppiyaki.pet.repository.PetRepository;
import com.ppiyaki.user.User;
import com.ppiyaki.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PetService {

    private final PetRepository petRepository;
    private final UserRepository userRepository;

    public PetService(
            final PetRepository petRepository,
            final UserRepository userRepository
    ) {
        this.petRepository = petRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PetResponse readMyPet(final Long userId) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getPet() == null) {
            throw new BusinessException(ErrorCode.PET_NOT_FOUND);
        }

        final Pet pet = petRepository.findById(user.getPet())
                .orElseThrow(() -> new BusinessException(ErrorCode.PET_NOT_FOUND));

        return PetResponse.from(pet);
    }
}
