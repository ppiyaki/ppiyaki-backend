package com.ppiyaki.user.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.user.CareMode;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.controller.dto.CareModeResponse;
import com.ppiyaki.user.controller.dto.MealTimesUpdateRequest;
import com.ppiyaki.user.controller.dto.UserMeResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.RefreshTokenRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserService(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository,
            final RefreshTokenRepository refreshTokenRepository
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
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

    @Transactional
    public UserMeResponse updateMealTimes(final Long userId, final MealTimesUpdateRequest request) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.updateMealTimes(request.breakfast(), request.lunch(), request.dinner());
        return UserMeResponse.from(user);
    }

    @Transactional
    public void withdraw(final Long userId) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);
        }

        final LocalDateTime now = LocalDateTime.now();

        if (user.getRole() == UserRole.CAREGIVER) {
            withdrawCaregiverWithSeniors(user, now);
        } else {
            withdrawSingleUser(user, now);
        }
    }

    private void withdrawCaregiverWithSeniors(final User caregiver, final LocalDateTime now) {
        final List<CareRelation> careRelations = careRelationRepository.findByCaregiverIdAndDeletedAtIsNull(caregiver
                .getId());

        for (final CareRelation relation : careRelations) {
            final User senior = userRepository.findById(relation.getSeniorId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            withdrawSingleUser(senior, now);
            relation.softDelete(now);
        }

        withdrawSingleUser(caregiver, now);
    }

    private void withdrawSingleUser(final User user, final LocalDateTime now) {
        user.softDelete(now);
        refreshTokenRepository.deleteByUserId(user.getId());
    }
}
