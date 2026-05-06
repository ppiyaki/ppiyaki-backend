package com.ppiyaki.user.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.user.SeniorDevice;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.controller.dto.SeniorDeviceListResponse;
import com.ppiyaki.user.controller.dto.SeniorDeviceResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.SeniorDeviceRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeniorDeviceService {

    private final SeniorDeviceRepository seniorDeviceRepository;
    private final CareRelationRepository careRelationRepository;
    private final UserRepository userRepository;

    public SeniorDeviceService(
            final SeniorDeviceRepository seniorDeviceRepository,
            final CareRelationRepository careRelationRepository,
            final UserRepository userRepository
    ) {
        this.seniorDeviceRepository = seniorDeviceRepository;
        this.careRelationRepository = careRelationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public SeniorDeviceListResponse readDevices(final Long caregiverId, final Long seniorId) {
        validateCaregiverRelation(caregiverId, seniorId);

        final List<SeniorDeviceResponse> responses = seniorDeviceRepository.findBySeniorId(seniorId)
                .stream()
                .map(SeniorDeviceResponse::from)
                .toList();

        return new SeniorDeviceListResponse(responses);
    }

    @Transactional
    public void revokeDevice(final Long caregiverId, final Long seniorId, final Long deviceId) {
        validateCaregiverRelation(caregiverId, seniorId);

        final SeniorDevice seniorDevice = seniorDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_DEVICE_NOT_FOUND));

        if (!seniorDevice.getSeniorId().equals(seniorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        seniorDevice.revoke();
    }

    @Transactional
    public void revokeAllDevices(final Long caregiverId, final Long seniorId) {
        validateCaregiverRelation(caregiverId, seniorId);

        final List<SeniorDevice> devices = seniorDeviceRepository.findBySeniorId(seniorId);
        devices.forEach(SeniorDevice::revoke);
    }

    private void validateCaregiverRelation(final Long caregiverId, final Long seniorId) {
        final var caregiver = userRepository.findById(caregiverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (caregiver.getRole() != UserRole.CAREGIVER) {
            throw new BusinessException(ErrorCode.CARE_RELATION_ROLE_MISMATCH);
        }

        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(caregiverId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
    }
}
