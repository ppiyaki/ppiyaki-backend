package com.ppiyaki.user.controller.dto;

public record SeniorCreateResponse(
        Long seniorId,
        Long careRelationId,
        Long petId
) {
}
