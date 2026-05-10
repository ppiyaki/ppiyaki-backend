package com.ppiyaki.pet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.ppiyaki.medication.LogStatus;
import com.ppiyaki.medication.MedicationLog;
import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.repository.PetRepository;
import com.ppiyaki.user.User;
import com.ppiyaki.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PetPointListenerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private com.ppiyaki.medication.repository.MedicationScheduleRepository medicationScheduleRepository;

    @Mock
    private com.ppiyaki.medication.repository.MedicationLogRepository medicationLogRepository;

    @Mock
    private BadgeService badgeService;

    private PetPointListener petPointListener;

    @BeforeEach
    void setUp() {
        petPointListener = new PetPointListener(
                userRepository, petRepository, medicationScheduleRepository,
                medicationLogRepository, badgeService, 10L);
    }

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

    @Test
    @DisplayName("하루 전체 복약 완료 시 streak이 증가한다")
    void onMedicationTaken_allTaken_incrementsStreak() {
        // given
        final User user = mock(User.class);
        lenient().when(user.getPet()).thenReturn(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        final Pet pet = Pet.create();
        given(petRepository.findById(1L)).willReturn(Optional.of(pet));

        final MedicationSchedule schedule1 = mock(MedicationSchedule.class);
        final MedicationSchedule schedule2 = mock(MedicationSchedule.class);
        given(medicationScheduleRepository.findActiveByOwnerAndDate(
                eq(1L), any()))
                .willReturn(List.of(schedule1, schedule2));

        final MedicationLog log1 = mock(MedicationLog.class);
        given(log1.getStatus()).willReturn(LogStatus.TAKEN);
        final MedicationLog log2 = mock(MedicationLog.class);
        given(log2.getStatus()).willReturn(LogStatus.TAKEN);
        given(medicationLogRepository.findBySeniorIdAndTargetDate(
                eq(1L), any()))
                .willReturn(List.of(log1, log2));

        // when
        petPointListener.onMedicationTaken(new MedicationTakenEvent(1L));

        // then
        assertThat(pet.getStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("로그 수가 스케줄 수를 초과하면 streak이 증가하지 않는다")
    void onMedicationTaken_moreLogsThanSchedules_noStreakIncrease() {
        // given
        final User user = mock(User.class);
        lenient().when(user.getPet()).thenReturn(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        final Pet pet = Pet.create();
        given(petRepository.findById(1L)).willReturn(Optional.of(pet));

        final MedicationSchedule schedule1 = mock(MedicationSchedule.class);
        final MedicationSchedule schedule2 = mock(MedicationSchedule.class);
        given(medicationScheduleRepository.findActiveByOwnerAndDate(
                eq(1L), any()))
                .willReturn(List.of(schedule1, schedule2));

        final MedicationLog log1 = mock(MedicationLog.class);
        given(log1.getStatus()).willReturn(LogStatus.TAKEN);
        final MedicationLog log2 = mock(MedicationLog.class);
        given(log2.getStatus()).willReturn(LogStatus.TAKEN);
        final MedicationLog log3 = mock(MedicationLog.class);
        given(log3.getStatus()).willReturn(LogStatus.TAKEN);
        given(medicationLogRepository.findBySeniorIdAndTargetDate(
                eq(1L), any()))
                .willReturn(List.of(log1, log2, log3));

        // when
        petPointListener.onMedicationTaken(new MedicationTakenEvent(1L));

        // then — takenCount(3) != totalSchedules(2) → streak 미증가
        assertThat(pet.getStreak()).isEqualTo(0);
    }

    @Test
    @DisplayName("일부 MISSED가 있으면 streak이 증가하지 않는다")
    void onMedicationTaken_someMissed_noStreakIncrease() {
        // given
        final User user = mock(User.class);
        lenient().when(user.getPet()).thenReturn(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        final Pet pet = Pet.create();
        given(petRepository.findById(1L)).willReturn(Optional.of(pet));

        final MedicationSchedule schedule1 = mock(MedicationSchedule.class);
        final MedicationSchedule schedule2 = mock(MedicationSchedule.class);
        given(medicationScheduleRepository.findActiveByOwnerAndDate(
                eq(1L), any()))
                .willReturn(List.of(schedule1, schedule2));

        final MedicationLog log1 = mock(MedicationLog.class);
        given(log1.getStatus()).willReturn(LogStatus.TAKEN);
        final MedicationLog log2 = mock(MedicationLog.class);
        given(log2.getStatus()).willReturn(LogStatus.MISSED);
        given(medicationLogRepository.findBySeniorIdAndTargetDate(
                eq(1L), any()))
                .willReturn(List.of(log1, log2));

        // when
        petPointListener.onMedicationTaken(new MedicationTakenEvent(1L));

        // then
        assertThat(pet.getStreak()).isEqualTo(0);
    }
}
