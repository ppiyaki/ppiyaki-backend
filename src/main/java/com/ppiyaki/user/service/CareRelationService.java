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
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final int MAX_INVITE_CODE_RETRIES = 3;

    @Transactional
    public InviteCodeResponse createInviteCode(final Long userId) {
        final User user = findUserById(userId);
        validateRole(user, UserRole.CAREGIVER);

        for (int attempt = 0; attempt < MAX_INVITE_CODE_RETRIES; attempt++) {
            final CareRelation careRelation = CareRelation.createInvite(user.getId(), LocalDateTime.now());
            try {
                careRelationRepository.saveAndFlush(careRelation);
                return new InviteCodeResponse(careRelation.getInviteCode(), careRelation.getExpiresAt());
            } catch (final DataIntegrityViolationException ignored) {
                // invite_code 중복 — 재생성 시도
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to generate unique invite code");
    }

    @Transactional
    public AcceptInviteResponse acceptInvite(final Long userId, final String inviteCode) {
        final User senior = findUserById(userId);
        validateRole(senior, UserRole.SENIOR);

        final CareRelation careRelation = careRelationRepository
                .findByInviteCodeAndSeniorIdIsNullAndDeletedAtIsNull(inviteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_INVITE_NOT_FOUND));

        if (careRelation.isExpired(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CARE_RELATION_INVITE_EXPIRED);
        }

        careRelationRepository
                .findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(careRelation.getCaregiverId(), senior.getId())
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.CARE_RELATION_ALREADY_EXISTS);
                });

        careRelation.acceptInvite(senior.getId());

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
