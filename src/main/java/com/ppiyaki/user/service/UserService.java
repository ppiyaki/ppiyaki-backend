package com.ppiyaki.user.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.infrastructure.storage.ProfileImageUrlResolver;
import com.ppiyaki.user.controller.dto.CareModeResponse;
import com.ppiyaki.user.controller.dto.MealTimesUpdateRequest;
import com.ppiyaki.user.controller.dto.ProfileUpdateRequest;
import com.ppiyaki.user.controller.dto.SeniorProfileUpdateRequest;
import com.ppiyaki.user.controller.dto.TimezoneUpdateRequest;
import com.ppiyaki.user.controller.dto.UserMeResponse;
import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.SupportedTimezone;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.domain.UserRole;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.RefreshTokenRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Pattern PROFILE_IMAGE_OBJECT_KEY_PATTERN = Pattern.compile(
            "^profile-image/(\\d+)/"
                    + "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                    + "\\.[a-zA-Z0-9]+$");

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    public UserService(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository,
            final RefreshTokenRepository refreshTokenRepository,
            final ProfileImageUrlResolver profileImageUrlResolver
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.profileImageUrlResolver = profileImageUrlResolver;
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
        return toUserMeResponse(user);
    }

    @Transactional
    public UserMeResponse updateMealTimesForSenior(
            final Long requesterId,
            final Long seniorId,
            final MealTimesUpdateRequest request
    ) {
        final User senior = userRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(requesterId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));

        senior.updateMealTimes(request.breakfast(), request.lunch(), request.dinner());
        return toUserMeResponse(senior);
    }

    @Transactional
    public UserMeResponse updateMyProfile(final Long userId, final ProfileUpdateRequest request) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (request.profileImageObjectKey() != null) {
            validateProfileImageObjectKey(request.profileImageObjectKey(), userId);
        }

        user.updateNickname(request.nickname());
        user.updateProfileImage(request.profileImage(), request.profileImageObjectKey());
        if (request.gender() != null) {
            user.updateGender(request.gender());
        }
        return toUserMeResponse(user);
    }

    @Transactional
    public UserMeResponse updateSeniorProfile(
            final Long requesterId,
            final Long seniorId,
            final SeniorProfileUpdateRequest request
    ) {
        final User senior = userRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(requesterId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));

        senior.updateNickname(request.nickname());
        senior.updateGender(request.gender());
        return toUserMeResponse(senior);
    }

    @Transactional
    public UserMeResponse updateMyTimezone(final Long userId, final TimezoneUpdateRequest request) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        validateTimezone(request.timezone());
        user.updateTimezone(request.timezone());
        return toUserMeResponse(user);
    }

    @Transactional
    public UserMeResponse updateSeniorTimezone(
            final Long requesterId,
            final Long seniorId,
            final TimezoneUpdateRequest request
    ) {
        final User senior = userRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(requesterId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));

        validateTimezone(request.timezone());
        senior.updateTimezone(request.timezone());
        return toUserMeResponse(senior);
    }

    private void validateTimezone(final String timezone) {
        if (!SupportedTimezone.isSupported(timezone)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Unsupported timezone: " + timezone);
        }
    }

    private UserMeResponse toUserMeResponse(final User user) {
        return UserMeResponse.from(user, profileImageUrlResolver.resolve(user.getProfileImageObjectKey()));
    }

    private void validateProfileImageObjectKey(final String objectKey, final Long userId) {
        final Matcher matcher = PROFILE_IMAGE_OBJECT_KEY_PATTERN.matcher(objectKey);
        if (objectKey.contains("..") || !matcher.matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid profile image objectKey format");
        }
        final long uploaderId = Long.parseLong(matcher.group(1));
        if (uploaderId != userId) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "objectKey owner mismatch");
        }
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
