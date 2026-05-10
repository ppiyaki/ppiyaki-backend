package com.ppiyaki.user.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.notification.NotificationSettings;
import com.ppiyaki.notification.repository.NotificationSettingsRepository;
import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.repository.PetRepository;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.controller.dto.OnboardingRequest;
import com.ppiyaki.user.controller.dto.OnboardingResponse;
import com.ppiyaki.user.controller.dto.OnboardingResponse.SeniorResult;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;
    private final PetRepository petRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;

    public OnboardingService(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository,
            final PetRepository petRepository,
            final NotificationSettingsRepository notificationSettingsRepository
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
        this.petRepository = petRepository;
        this.notificationSettingsRepository = notificationSettingsRepository;
    }

    @Transactional
    public OnboardingResponse onboard(final Long caregiverId, final OnboardingRequest onboardingRequest) {
        final User caregiver = userRepository.findById(caregiverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (caregiver.getRole() != UserRole.CAREGIVER) {
            throw new BusinessException(ErrorCode.CARE_RELATION_ROLE_MISMATCH);
        }

        caregiver.updateNickname(onboardingRequest.nickname());

        final List<SeniorResult> seniorResults = new ArrayList<>();

        for (final OnboardingRequest.SeniorEntry seniorEntry : onboardingRequest.seniors()) {
            final User senior = userRepository.save(
                    User.createSenior(seniorEntry.nickname(), seniorEntry.gender()));

            final Pet pet = petRepository.save(Pet.create());
            senior.assignPet(pet.getId());

            careRelationRepository.save(CareRelation.createLinked(senior.getId(), caregiverId));

            notificationSettingsRepository.save(
                    NotificationSettings.createWithStandardPreset(caregiverId, senior.getId()));

            seniorResults.add(new SeniorResult(senior.getId(), senior.getNickname(), pet.getId()));
        }

        return new OnboardingResponse(caregiver.getNickname(), seniorResults);
    }
}
