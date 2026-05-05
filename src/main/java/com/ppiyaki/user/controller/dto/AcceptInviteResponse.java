package com.ppiyaki.user.controller.dto;

public record AcceptInviteResponse(
        Long careRelationId,
        Long seniorId,
        Long caregiverId
) {
}
