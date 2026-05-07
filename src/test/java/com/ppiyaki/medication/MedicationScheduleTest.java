package com.ppiyaki.medication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MedicationSchedule 생성자/update 도메인 검증")
class MedicationScheduleTest {

    @Test
    @DisplayName("mealSlot null이면 NPE")
    void nullMealSlot_throws() {
        assertThatThrownBy(() -> new MedicationSchedule(
                1L, null, "1정", "DAILY", LocalDate.now(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("update에 mealSlot null이면 기존 값 유지")
    void update_keepsMealSlotWhenNull() {
        final MedicationSchedule schedule = new MedicationSchedule(
                1L, MealSlot.BREAKFAST, "1정", "DAILY", LocalDate.now(), null);

        schedule.update(null, "2정", null, null, null);

        assertThat(schedule.getMealSlot()).isEqualTo(MealSlot.BREAKFAST);
        assertThat(schedule.getDosage()).isEqualTo("2정");
    }

    @Test
    @DisplayName("update로 슬롯 변경 가능")
    void update_changesMealSlot() {
        final MedicationSchedule schedule = new MedicationSchedule(
                1L, MealSlot.BREAKFAST, "1정", "DAILY", LocalDate.now(), null);

        schedule.update(MealSlot.DINNER, null, null, null, null);

        assertThat(schedule.getMealSlot()).isEqualTo(MealSlot.DINNER);
    }
}
