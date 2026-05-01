package com.ppiyaki.user.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.user.CareMode;
import com.ppiyaki.user.User;
import com.ppiyaki.user.controller.dto.CareModeResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;

    public UserService(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
    }

    @Transactional
    public CareModeResponse updateCareMode(
            final Long requesterId,
            final Long seniorId,
            final CareMode careMode
    ) {
        final User senior = userRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(requesterId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));

        senior.changeCareMode(careMode);
        return new CareModeResponse(senior.getId(), senior.getCareMode());
    }
}
