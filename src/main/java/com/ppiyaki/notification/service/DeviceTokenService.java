package com.ppiyaki.notification.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.notification.DevicePlatform;
import com.ppiyaki.notification.DeviceToken;
import com.ppiyaki.notification.controller.dto.DeviceTokenResponse;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceTokenService(final DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Transactional
    public DeviceTokenResponse register(final Long userId, final String token, final DevicePlatform platform) {
        final DeviceToken saved = deviceTokenRepository.findByToken(token)
                .map(existing -> {
                    existing.reactivate(LocalDateTime.now());
                    return existing;
                })
                .orElseGet(() -> deviceTokenRepository.save(DeviceToken.register(userId, token, platform)));
        return DeviceTokenResponse.from(saved);
    }

    @Transactional
    public void deactivate(final Long userId, final Long tokenId) {
        final DeviceToken deviceToken = deviceTokenRepository.findById(tokenId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_TOKEN_NOT_FOUND));
        if (!deviceToken.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.DEVICE_TOKEN_FORBIDDEN);
        }
        deviceToken.deactivate();
    }
}
