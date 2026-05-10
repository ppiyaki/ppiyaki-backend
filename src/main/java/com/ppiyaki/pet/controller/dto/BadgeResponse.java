package com.ppiyaki.pet.controller.dto;

import com.ppiyaki.pet.Badge;
import java.time.LocalDateTime;

public record BadgeResponse(
        String badgeType,
        String displayName,
        String description,
        LocalDateTime createdAt
) {

    public static BadgeResponse from(final Badge badge) {
        return new BadgeResponse(
                badge.getBadgeType().name(),
                badge.getBadgeType().getDisplayName(),
                badge.getBadgeType().getDescription(),
                badge.getCreatedAt()
        );
    }
}
