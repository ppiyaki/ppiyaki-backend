package com.ppiyaki.pet.controller.dto;

import com.ppiyaki.pet.Badge;
import com.ppiyaki.pet.Pet;
import java.util.List;

public record PetResponse(
        Long id,
        long point,
        int level,
        String stage,
        int streak,
        List<BadgeResponse> badges
) {

    public static PetResponse from(final Pet pet, final List<Badge> badges) {
        final List<BadgeResponse> badgeResponses = badges.stream()
                .map(BadgeResponse::from)
                .toList();
        return new PetResponse(
                pet.getId(),
                pet.getPoint(),
                pet.getLevel(),
                pet.getHighestStage().name(),
                pet.getStreak(),
                badgeResponses
        );
    }
}
