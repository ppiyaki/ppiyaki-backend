package com.ppiyaki.user.controller.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record SeniorCreateRequest(
        @NotBlank String nickname,
        LocalDate dob
) {
}
