package com.ppiyaki.common.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ncp.storage", name = "bucket-name")
public class PhotoUrlAssembler {

    private final NcpStorageProperties properties;

    public PhotoUrlAssembler(final NcpStorageProperties properties) {
        this.properties = properties;
    }

    public String toFullUrl(final String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        final String trimmedEndpoint = properties.endpoint().replaceAll("/+$", "");
        return trimmedEndpoint + "/" + properties.bucketName() + "/" + objectKey;
    }
}
