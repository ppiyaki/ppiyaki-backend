package com.ppiyaki.infrastructure.storage.dto;

import java.time.Instant;

public record PresignedUploadResponse(
        String objectKey,
        String presignedUrl,
        Instant expiresAt
) {
}
