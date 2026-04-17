package com.ppiyaki.chat.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(@NotBlank String message) {
}
