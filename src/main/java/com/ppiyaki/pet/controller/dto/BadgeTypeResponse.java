package com.ppiyaki.pet.controller.dto;

import com.ppiyaki.pet.BadgeType;

public record BadgeTypeResponse(
        String badgeType,
        String displayName,
        String description
) {

    public static BadgeTypeResponse from(final BadgeType badgeType) {
        return new BadgeTypeResponse(
                badgeType.name(),
                badgeType.getDisplayName(),
                badgeType.getDescription()
        );
    }
}
