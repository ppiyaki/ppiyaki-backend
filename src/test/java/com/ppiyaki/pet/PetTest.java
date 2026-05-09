package com.ppiyaki.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PetTest {

    @Test
    @DisplayName("create()으로 생성하면 point=0, streak=0, stage=EGG이다")
    void create_initialState() {
        // given & when
        final Pet pet = Pet.create();

        // then
        assertThat(pet.getPoint()).isEqualTo(0L);
        assertThat(pet.getLevel()).isEqualTo(0);
        assertThat(pet.getCurrentStreak()).isEqualTo(0);
        assertThat(pet.getStage()).isEqualTo(PetStage.EGG);
    }

    @Test
    @DisplayName("addPoint로 포인트가 증가한다")
    void addPoint_increasesPoint() {
        // given
        final Pet pet = Pet.create();

        // when
        pet.addPoint(10L);

        // then
        assertThat(pet.getPoint()).isEqualTo(10L);
    }

    @Test
    @DisplayName("addPoint에 0 이하를 넣으면 예외가 발생한다")
    void addPoint_zeroOrNegative_throwsException() {
        // given
        final Pet pet = Pet.create();

        // when & then
        assertThatThrownBy(() -> pet.addPoint(0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("레벨은 floor(sqrt(point / 10))으로 계산된다")
    void getLevel_calculatesCorrectly() {
        // given & when & then
        assertThat(new Pet(0L).getLevel()).isEqualTo(0);
        assertThat(new Pet(10L).getLevel()).isEqualTo(1);
        assertThat(new Pet(40L).getLevel()).isEqualTo(2);
        assertThat(new Pet(90L).getLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("incrementStreak으로 streak이 증가하고 stage가 올라간다")
    void incrementStreak_increasesStreakAndStage() {
        // given
        final Pet pet = Pet.create();
        final LocalDate today = LocalDate.now();

        // when
        for (int i = 0; i < 7; i++) {
            pet.incrementStreak(today.plusDays(i));
        }

        // then
        assertThat(pet.getCurrentStreak()).isEqualTo(7);
        assertThat(pet.getStage()).isEqualTo(PetStage.BABY);
    }

    @Test
    @DisplayName("같은 날짜로 incrementStreak을 호출하면 streak이 증가하지 않는다")
    void incrementStreak_sameDate_noIncrease() {
        // given
        final Pet pet = Pet.create();
        final LocalDate today = LocalDate.now();

        // when
        pet.incrementStreak(today);
        pet.incrementStreak(today);

        // then
        assertThat(pet.getCurrentStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("streak 3일 달성 시 CRACKED_EGG 단계가 된다")
    void stage_crackedEgg_at3days() {
        // given
        final Pet pet = Pet.create();
        final LocalDate today = LocalDate.now();

        // when
        for (int i = 0; i < 3; i++) {
            pet.incrementStreak(today.plusDays(i));
        }

        // then
        assertThat(pet.getStage()).isEqualTo(PetStage.CRACKED_EGG);
    }

    @Test
    @DisplayName("PetStage.fromStreak으로 올바른 단계를 반환한다")
    void petStage_fromStreak() {
        // given & when & then
        assertThat(PetStage.fromStreak(0)).isEqualTo(PetStage.EGG);
        assertThat(PetStage.fromStreak(3)).isEqualTo(PetStage.CRACKED_EGG);
        assertThat(PetStage.fromStreak(7)).isEqualTo(PetStage.BABY);
        assertThat(PetStage.fromStreak(14)).isEqualTo(PetStage.HEALTHY);
        assertThat(PetStage.fromStreak(30)).isEqualTo(PetStage.GUARDIAN);
        assertThat(PetStage.fromStreak(100)).isEqualTo(PetStage.EMPEROR);
        assertThat(PetStage.fromStreak(5)).isEqualTo(PetStage.CRACKED_EGG);
    }
}
