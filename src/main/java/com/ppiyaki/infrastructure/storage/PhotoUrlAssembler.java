package com.ppiyaki.infrastructure.storage;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@ConditionalOnProperty(prefix = "ncp.storage", name = "bucket-name")
public class PhotoUrlAssembler {

    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(30);

    private final NcpStorageProperties properties;
    private final S3Presigner s3Presigner;

    public PhotoUrlAssembler(final NcpStorageProperties properties, final S3Presigner s3Presigner) {
        this.properties = properties;
        this.s3Presigner = s3Presigner;
    }

    public String toFullUrl(final String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        final GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(objectKey)
                .build();
        final GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(DOWNLOAD_URL_TTL)
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}
