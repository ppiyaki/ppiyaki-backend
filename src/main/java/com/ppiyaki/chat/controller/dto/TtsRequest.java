package com.ppiyaki.chat.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record TtsRequest(@NotBlank String text) {
}
