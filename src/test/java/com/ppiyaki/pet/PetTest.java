package com.ppiyaki.pet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PetTest {

    @Test
    void 생성자_point값을_보존한다() {
        Pet pet = new Pet(100L);

        assertThat(pet.getPoint()).isEqualTo(100L);
    }

    @Test
    void 생성자_point가_0이어도_허용한다() {
        Pet pet = new Pet(0L);

        assertThat(pet.getPoint()).isEqualTo(0L);
    }
}
