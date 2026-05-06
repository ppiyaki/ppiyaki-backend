package com.ppiyaki.common.auth;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService {

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;

    public AuthorizationService(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
    }

    @Transactional(readOnly = true)
    public void requireCaregiver(final Long userId) {
        final User user = findUser(userId);
        if (user.getRole() != UserRole.CAREGIVER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only caregivers can perform this action");
        }
    }

    @Transactional(readOnly = true)
    public void requireSeniorSelfAccess(final Long userId, final Long seniorId) {
        final User user = findUser(userId);
        if (user.getRole() == UserRole.SENIOR && !userId.equals(seniorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Senior can only access own data");
        }
        if (user.getRole() == UserRole.CAREGIVER) {
            careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(userId, seniorId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
        }
    }

    private User findUser(final Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
