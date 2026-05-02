package com.ppiyaki.pet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.controller.dto.PetResponse;
import com.ppiyaki.pet.repository.PetRepository;
import com.ppiyaki.user.User;
import com.ppiyaki.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PetServiceTest {

    @Mock
    private PetRepository petRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PetService petService;

    @Test
    @DisplayName("유저에게 펫이 있으면 포인트와 레벨을 반환한다")
    void readMyPet_success() {
        // given
        final User user = mock(User.class);
        lenient().when(user.getPet()).thenReturn(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        final Pet pet = Pet.create();
        pet.addPoint(40L);
        given(petRepository.findById(1L)).willReturn(Optional.of(pet));

        // when
        final PetResponse petResponse = petService.readMyPet(1L);

        // then
        assertThat(petResponse.point()).isEqualTo(40L);
        assertThat(petResponse.level()).isEqualTo(2);
    }

    @Test
    @DisplayName("유저에게 펫이 없으면 PET_NOT_FOUND 에러가 발생한다")
    void readMyPet_noPet_throwsNotFound() {
        // given
        final User user = mock(User.class);
        lenient().when(user.getPet()).thenReturn(null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> petService.readMyPet(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    final BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.PET_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("유저가 존재하지 않으면 USER_NOT_FOUND 에러가 발생한다")
    void readMyPet_userNotFound_throwsNotFound() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> petService.readMyPet(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    final BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                });
    }
}
