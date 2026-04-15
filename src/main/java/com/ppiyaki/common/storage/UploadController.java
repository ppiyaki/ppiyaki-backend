package com.ppiyaki.common.storage;

import com.ppiyaki.common.storage.dto.PresignedUploadRequest;
import com.ppiyaki.common.storage.dto.PresignedUploadResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uploads")
@ConditionalOnProperty(prefix = "ncp.storage", name = "bucket-name")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(final UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/presigned")
    public ResponseEntity<PresignedUploadResponse> createPresigned(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final PresignedUploadRequest request
    ) {
        return ResponseEntity.ok(uploadService.createPresignedPutUrl(userId, request));
    }
}
