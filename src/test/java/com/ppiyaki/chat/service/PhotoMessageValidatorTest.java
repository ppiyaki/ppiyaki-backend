package com.ppiyaki.chat.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("PhotoMessageValidator: MIME/사이즈/빈 파일 검증")
class PhotoMessageValidatorTest {

    @Test
    @DisplayName("정상 jpeg 파일 통과")
    void valid_jpeg() {
        final MockMultipartFile file = new MockMultipartFile(
                "file", "p.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatCode(() -> PhotoMessageValidator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정상 png 파일 통과")
    void valid_png() {
        final MockMultipartFile file = new MockMultipartFile(
                "file", "p.png", "image/png", new byte[]{1});

        assertThatCode(() -> PhotoMessageValidator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정상 webp 파일 통과")
    void valid_webp() {
        final MockMultipartFile file = new MockMultipartFile(
                "file", "p.webp", "image/webp", new byte[]{1});

        assertThatCode(() -> PhotoMessageValidator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("빈 파일 → CHAT_PHOTO_FILE_EMPTY")
    void empty_file_throws() {
        final MockMultipartFile file = new MockMultipartFile(
                "file", "p.jpg", "image/jpeg", new byte[]{});

        assertThatThrownBy(() -> PhotoMessageValidator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_PHOTO_FILE_EMPTY);
    }

    @Test
    @DisplayName("null → CHAT_PHOTO_FILE_EMPTY")
    void null_file_throws() {
        assertThatThrownBy(() -> PhotoMessageValidator.validate(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_PHOTO_FILE_EMPTY);
    }

    @Test
    @DisplayName("image/gif 거절 → CHAT_PHOTO_TYPE_NOT_SUPPORTED")
    void unsupported_mime_throws() {
        final MockMultipartFile file = new MockMultipartFile(
                "file", "p.gif", "image/gif", new byte[]{1});

        assertThatThrownBy(() -> PhotoMessageValidator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_PHOTO_TYPE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("contentType null → CHAT_PHOTO_TYPE_NOT_SUPPORTED")
    void null_contentType_throws() {
        final MockMultipartFile file = new MockMultipartFile(
                "file", "p.jpg", null, new byte[]{1});

        assertThatThrownBy(() -> PhotoMessageValidator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_PHOTO_TYPE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("10MB 초과 → CHAT_PHOTO_TOO_LARGE")
    void oversize_throws() {
        final byte[] big = new byte[(int) PhotoMessageValidator.MAX_BYTES + 1];
        final MockMultipartFile file = new MockMultipartFile(
                "file", "p.jpg", "image/jpeg", big);

        assertThatThrownBy(() -> PhotoMessageValidator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_PHOTO_TOO_LARGE);
    }
}
