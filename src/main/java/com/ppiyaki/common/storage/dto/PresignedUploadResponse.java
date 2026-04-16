package com.ppiyaki.common.storage.dto;

import java.time.Instant;

public record PresignedUploadResponse(
        String objectKey,
        String presignedUrl,
        Instant expiresAt
) {
}
