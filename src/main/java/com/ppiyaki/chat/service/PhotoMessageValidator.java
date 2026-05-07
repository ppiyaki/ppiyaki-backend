package com.ppiyaki.chat.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

public final class PhotoMessageValidator {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private PhotoMessageValidator() {
    }

    /**
     * 채팅 사진 메시지 multipart 파일 검증.
     * spec chat-photo-messages.md §3 입력 검증 규칙.
     */
    public static void validate(final MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.CHAT_PHOTO_FILE_EMPTY);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.CHAT_PHOTO_TOO_LARGE);
        }
        final String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            throw new BusinessException(ErrorCode.CHAT_PHOTO_TYPE_NOT_SUPPORTED);
        }
    }
}
