package com.ppiyaki.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.repository.PetRepository;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.controller.dto.SeniorCreateRequest;
import com.ppiyaki.user.controller.dto.SeniorCreateResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
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
        final User caregiver = mock(User.class);
        lenient().when(caregiver.getId()).thenReturn(1L);
        lenient().when(caregiver.getRole()).thenReturn(UserRole.CAREGIVER);
        given(userRepository.findById(1L)).willReturn(Optional.of(caregiver));

        final User senior = mock(User.class);
        lenient().when(senior.getId()).thenReturn(2L);
        given(userRepository.save(any(User.class))).willReturn(senior);

        final Pet pet = mock(Pet.class);
        lenient().when(pet.getId()).thenReturn(1L);
        given(petRepository.save(any(Pet.class))).willReturn(pet);

        final CareRelation careRelation = mock(CareRelation.class);
        lenient().when(careRelation.getId()).thenReturn(1L);
        given(careRelationRepository.save(any(CareRelation.class))).willReturn(careRelation);

        final SeniorCreateRequest request = new SeniorCreateRequest("시니어할머니", LocalDate.of(1945, 3, 15));

        // when
        final SeniorCreateResponse response = seniorService.createSenior(1L, request);

        // then
        assertThat(response.seniorId()).isEqualTo(2L);
        assertThat(response.careRelationId()).isEqualTo(1L);
        assertThat(response.petId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("시니어가 시니어 대리 생성을 시도하면 ROLE_MISMATCH 에러가 발생한다")
    void createSenior_bySenior_throwsRoleMismatch() {
        // given
        final User senior = mock(User.class);
        lenient().when(senior.getRole()).thenReturn(UserRole.SENIOR);
        given(userRepository.findById(1L)).willReturn(Optional.of(senior));

        final SeniorCreateRequest request = new SeniorCreateRequest("시니어", null);

        // when & then
        assertThatThrownBy(() -> seniorService.createSenior(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    final BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CARE_RELATION_ROLE_MISMATCH);
                });
    }
}
