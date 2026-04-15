package com.ppiyaki.common.storage.dto;

import com.ppiyaki.common.storage.UploadPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PresignedUploadRequest(
        @NotNull UploadPurpose purpose,
        @NotBlank @Pattern(regexp = "jpg|jpeg|png|webp", message = "extension must be one of: jpg, jpeg, png, webp") String extension,
        @NotBlank String contentType
) {
}
