package com.ppiyaki.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.repository.PetRepository;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.User;
import com.ppiyaki.user.controller.dto.SeniorCreateRequest;
import com.ppiyaki.user.controller.dto.SeniorCreateResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeniorServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CareRelationRepository careRelationRepository;

    @Mock
    private PetRepository petRepository;

    @InjectMocks
    private SeniorService seniorService;

    @Test
    @DisplayName("보호자가 시니어를 대리 생성하면 시니어 계정, CareRelation, Pet이 함께 생성된다")
    void createSenior_success() {
        // given
        final User senior = mock(User.class);
        given(senior.getId()).willReturn(2L);
        given(userRepository.save(any(User.class))).willReturn(senior);

        final Pet pet = mock(Pet.class);
        given(pet.getId()).willReturn(1L);
        given(petRepository.save(any(Pet.class))).willReturn(pet);

        final CareRelation careRelation = mock(CareRelation.class);
        given(careRelation.getId()).willReturn(1L);
        given(careRelationRepository.save(any(CareRelation.class))).willReturn(careRelation);

        final SeniorCreateRequest request = new SeniorCreateRequest("시니어할머니", LocalDate.of(1945, 3, 15));

        // when
        final SeniorCreateResponse response = seniorService.createSenior(1L, request);

        // then
        assertThat(response.seniorId()).isEqualTo(2L);
        assertThat(response.careRelationId()).isEqualTo(1L);
        assertThat(response.petId()).isEqualTo(1L);
    }

}
