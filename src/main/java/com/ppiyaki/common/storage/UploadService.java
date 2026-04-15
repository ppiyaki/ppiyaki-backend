package com.ppiyaki.common.storage;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.storage.dto.PresignedUploadRequest;
import com.ppiyaki.common.storage.dto.PresignedUploadResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@ConditionalOnProperty(prefix = "ncp.storage", name = "bucket-name")
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    private static final Duration PRESIGN_TTL = Duration.ofMinutes(5);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final S3Presigner s3Presigner;
    private final NcpStorageProperties properties;

    public UploadService(final S3Presigner s3Presigner, final NcpStorageProperties properties) {
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    public PresignedUploadResponse createPresignedPutUrl(
            final Long userId,
            final PresignedUploadRequest request
    ) {
        validateContentType(request.contentType());

        final String objectKey = buildObjectKey(userId, request);

        final PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(objectKey)
                .contentType(request.contentType())
                .build();

        final PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .putObjectRequest(putObjectRequest)
                .build();

        final PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        log.info("Presigned upload URL issued: purpose={} userId={} objectKey={}",
                request.purpose(), userId, objectKey);

        return new PresignedUploadResponse(
                objectKey,
                presigned.url().toString(),
                Instant.now().plus(PRESIGN_TTL)
        );
    }

    private void validateContentType(final String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "Unsupported content type: " + contentType);
        }
    }

    private String buildObjectKey(final Long userId, final PresignedUploadRequest request) {
        return "%s/%d/%s.%s".formatted(
                request.purpose().getPrefix(),
                userId,
                UUID.randomUUID(),
                request.extension()
        );
    }
}
