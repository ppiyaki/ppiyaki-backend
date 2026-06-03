package com.ppiyaki.infrastructure.storage;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 프로필 사진 objectKey를 presigned GET URL로 변환한다.
 *
 * <p>{@link PhotoUrlAssembler}는 {@code ncp.storage} 설정이 있을 때만 등록되는 조건부 빈이다.
 * {@code UserService} 등 항상 떠야 하는 코어 빈이 직접 의존하면 로컬/테스트 환경에서 기동이 깨지므로,
 * {@link ObjectProvider}로 감싸 스토리지 미설정 시 null을 반환한다.
 */
@Component
public class ProfileImageUrlResolver {

    private final ObjectProvider<PhotoUrlAssembler> photoUrlAssemblerProvider;

    public ProfileImageUrlResolver(final ObjectProvider<PhotoUrlAssembler> photoUrlAssemblerProvider) {
        this.photoUrlAssemblerProvider = photoUrlAssemblerProvider;
    }

    public String resolve(final String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        final PhotoUrlAssembler photoUrlAssembler = photoUrlAssemblerProvider.getIfAvailable();
        if (photoUrlAssembler == null) {
            return null;
        }
        return photoUrlAssembler.toFullUrl(objectKey);
    }
}
