package com.ppiyaki.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.storage.dto.PresignedUploadRequest;
import com.ppiyaki.common.storage.dto.PresignedUploadResponse;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class UploadServiceTest {

    private static final String MOCK_PRESIGNED_URL = "https://kr.object.ncloudstorage.com/ppiyaki-test/prescription/1/uuid.jpg?X-Amz-Signature=mock";

    private S3Presigner s3Presigner;
    private UploadService uploadService;

    @BeforeEach
    void setUp() throws Exception {
        s3Presigner = mock(S3Presigner.class);
        final NcpStorageProperties properties = new NcpStorageProperties(
                "https://kr.object.ncloudstorage.com",
                "kr-standard",
                "test-access-key",
                "test-secret-key",
                "ppiyaki-test"
        );
        uploadService = new UploadService(s3Presigner, properties);

        final PresignedPutObjectRequest presignedResponse = mock(PresignedPutObjectRequest.class);
        when(presignedResponse.url()).thenReturn(URI.create(MOCK_PRESIGNED_URL).toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedResponse);
    }

    @Test
    @DisplayName("유효한 요청이면 presigned URL과 objectKey를 반환한다")
    void createPresignedPutUrl_success() {
        // given
        final Long userId = 42L;
        final PresignedUploadRequest request = new PresignedUploadRequest(
                UploadPurpose.PRESCRIPTION, "jpg", "image/jpeg");

        // when
        final PresignedUploadResponse response = uploadService.createPresignedPutUrl(userId, request);

        // then
        assertThat(response.presignedUrl()).isEqualTo(MOCK_PRESIGNED_URL);
        assertThat(response.expiresAt()).isNotNull();
        assertThat(response.objectKey())
                .startsWith("prescription/42/")
                .endsWith(".jpg");
    }

    @Test
    @DisplayName("objectKey는 purpose prefix, userId, uuid, extension 순으로 조합된다")
    void createPresignedPutUrl_objectKeyFormat() {
        // given
        final Long userId = 7L;
        final PresignedUploadRequest request = new PresignedUploadRequest(
                UploadPurpose.MEDICATION_LOG, "png", "image/png");

        // when
        final PresignedUploadResponse response = uploadService.createPresignedPutUrl(userId, request);

        // then
        assertThat(response.objectKey())
                .startsWith("medication-log/7/")
                .endsWith(".png");
    }

    @Test
    @DisplayName("허용되지 않은 Content-Type이면 INVALID_INPUT 예외를 던진다")
    void createPresignedPutUrl_unsupportedContentType() {
        // given
        final PresignedUploadRequest request = new PresignedUploadRequest(
                UploadPurpose.PROFILE_IMAGE, "gif", "image/gif");

        // when & then
        assertThatThrownBy(() -> uploadService.createPresignedPutUrl(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
