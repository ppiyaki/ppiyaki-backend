package com.ppiyaki.pet.controller.dto;

import com.ppiyaki.pet.Pet;

public record PetResponse(
        Long id,
        long point,
        int level,
        String stage,
        int streak
) {

    public static PetResponse from(final Pet pet) {
        return new PetResponse(
                pet.getId(),
                pet.getPoint(),
                pet.getLevel(),
                pet.getStage().name(),
                pet.getCurrentStreak()
        );
    }
}
