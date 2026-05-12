package com.ppiyaki.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ppiyaki.notification.NotificationSettings;
import com.ppiyaki.notification.repository.NotificationSettingsRepository;
import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.repository.PetRepository;
import com.ppiyaki.user.CareMode;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.Gender;
import com.ppiyaki.user.User;
import com.ppiyaki.user.controller.dto.OnboardingRequest;
import com.ppiyaki.user.controller.dto.OnboardingRequest.SeniorEntry;
import com.ppiyaki.user.controller.dto.OnboardingResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CareRelationRepository careRelationRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private NotificationSettingsRepository notificationSettingsRepository;

    @InjectMocks
    private OnboardingService onboardingService;

    @Test
    @DisplayName("온보딩 시 보호자 닉네임 변경 + 시니어 N명 생성된다")
    void onboard_success() {
        // given
        final User caregiver = mock(User.class);
        given(caregiver.getNickname()).willReturn("보호자닉네임");
        given(userRepository.findById(1L)).willReturn(Optional.of(caregiver));

        final User senior1 = mock(User.class);
        given(senior1.getId()).willReturn(2L);
        given(senior1.getNickname()).willReturn("할머니");

        final User senior2 = mock(User.class);
        given(senior2.getId()).willReturn(3L);
        given(senior2.getNickname()).willReturn("할아버지");

        given(userRepository.save(any(User.class))).willReturn(senior1, senior2);

        final Pet pet1 = mock(Pet.class);
        given(pet1.getId()).willReturn(1L);
        final Pet pet2 = mock(Pet.class);
        given(pet2.getId()).willReturn(2L);
        given(petRepository.save(any(Pet.class))).willReturn(pet1, pet2);

        given(careRelationRepository.save(any(CareRelation.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        final OnboardingRequest onboardingRequest = new OnboardingRequest(
                "보호자닉네임",
                List.of(
                        new SeniorEntry("할머니", Gender.FEMALE, CareMode.AUTONOMOUS),
                        new SeniorEntry("할아버지", Gender.MALE, CareMode.MANAGED)
                )
        );

        // when
        final OnboardingResponse response = onboardingService.onboard(1L, onboardingRequest);

        // then
        assertThat(response.caregiverNickname()).isEqualTo("보호자닉네임");
        assertThat(response.responses()).hasSize(2);
        assertThat(response.responses().get(0).nickname()).isEqualTo("할머니");
        assertThat(response.responses().get(1).nickname()).isEqualTo("할아버지");
        verify(notificationSettingsRepository, times(2)).save(any(NotificationSettings.class));
        verify(senior1).changeCareMode(CareMode.AUTONOMOUS);
        verify(senior2).changeCareMode(CareMode.MANAGED);
    }

}
