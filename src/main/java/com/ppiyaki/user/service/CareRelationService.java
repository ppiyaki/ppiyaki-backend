package com.ppiyaki.user.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.controller.dto.AcceptInviteResponse;
import com.ppiyaki.user.controller.dto.InviteCodeResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareRelationService {

    private final CareRelationRepository careRelationRepository;
    private final UserRepository userRepository;

    public CareRelationService(
            final CareRelationRepository careRelationRepository,
            final UserRepository userRepository
    ) {
        this.careRelationRepository = careRelationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public InviteCodeResponse createInviteCode(final Long userId) {
        final User user = findUserById(userId);
        validateRole(user, UserRole.SENIOR);

        final CareRelation careRelation = CareRelation.createInvite(user.getId(), LocalDateTime.now());
        careRelationRepository.save(careRelation);

        return new InviteCodeResponse(careRelation.getInviteCode(), careRelation.getExpiresAt());
    }

    @Transactional
    public AcceptInviteResponse acceptInvite(final Long userId, final String inviteCode) {
        final User caregiver = findUserById(userId);
        validateRole(caregiver, UserRole.CAREGIVER);

        final CareRelation careRelation = careRelationRepository
                .findByInviteCodeAndCaregiverIdIsNull(inviteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_INVITE_NOT_FOUND));

        if (careRelation.isExpired(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CARE_RELATION_INVITE_EXPIRED);
        }

        careRelationRepository
                .findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(caregiver.getId(), careRelation.getSeniorId())
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.CARE_RELATION_ALREADY_EXISTS);
                });

        careRelation.acceptInvite(caregiver.getId());

        return new AcceptInviteResponse(
                careRelation.getId(),
                careRelation.getSeniorId(),
                careRelation.getCaregiverId()
        );
    }

    private User findUserById(final Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateRole(final User user, final UserRole expectedRole) {
        if (user.getRole() != expectedRole) {
            throw new BusinessException(ErrorCode.CARE_RELATION_ROLE_MISMATCH);
        }
    }
}
