package com.ppiyaki.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PetTest {

    @Test
    @DisplayName("create()으로 생성하면 point=0, level=0이다")
    void create_initialState() {
        // given & when
        final Pet pet = Pet.create();

        // then
        assertThat(pet.getPoint()).isEqualTo(0L);
        assertThat(pet.getLevel()).isEqualTo(0);
    }

    @Test
    @DisplayName("생성자 point값을 보존한다")
    void constructor_preservesPoint() {
        // given & when
        final Pet pet = new Pet(100L);

        // then
        assertThat(pet.getPoint()).isEqualTo(100L);
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
        assertThatThrownBy(() -> pet.addPoint(-5L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("레벨은 floor(sqrt(point / 10))으로 계산된다")
    void getLevel_calculatesCorrectly() {
        // given & when & then
        assertThat(new Pet(0L).getLevel()).isEqualTo(0);   // sqrt(0) = 0
        assertThat(new Pet(10L).getLevel()).isEqualTo(1);  // sqrt(1) = 1
        assertThat(new Pet(40L).getLevel()).isEqualTo(2);  // sqrt(4) = 2
        assertThat(new Pet(90L).getLevel()).isEqualTo(3);  // sqrt(9) = 3
        assertThat(new Pet(30L).getLevel()).isEqualTo(1);  // sqrt(3) = 1.73 → 1
        assertThat(new Pet(160L).getLevel()).isEqualTo(4); // sqrt(16) = 4
    }
}
