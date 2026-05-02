package com.ppiyaki.pet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.pet.Pet;
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
class PetPointListenerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PetRepository petRepository;

    @InjectMocks
    private PetPointListener petPointListener;

    @Test
    @DisplayName("복약 성공 이벤트 수신 시 펫 포인트가 10 증가한다")
    void onMedicationTaken_addsPoint() {
        // given
        final User user = mock(User.class);
        lenient().when(user.getPet()).thenReturn(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        final Pet pet = Pet.create();
        given(petRepository.findById(1L)).willReturn(Optional.of(pet));

        // when
        petPointListener.onMedicationTaken(new MedicationTakenEvent(1L));

        // then
        assertThat(pet.getPoint()).isEqualTo(10L);
    }

    @Test
    @DisplayName("펫이 연결되지 않은 유저의 이벤트는 무시한다")
    void onMedicationTaken_noPet_skips() {
        // given
        final User user = mock(User.class);
        lenient().when(user.getPet()).thenReturn(null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        petPointListener.onMedicationTaken(new MedicationTakenEvent(1L));

        // then — 예외 없이 정상 종료
    }

    @Test
    @DisplayName("존재하지 않는 유저의 이벤트는 무시한다")
    void onMedicationTaken_userNotFound_skips() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when
        petPointListener.onMedicationTaken(new MedicationTakenEvent(999L));

        // then — 예외 없이 정상 종료
    }
}
