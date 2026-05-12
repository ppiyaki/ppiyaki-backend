package com.ppiyaki.medication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MedicationSchedule 생성자/update 도메인 검증")
class MedicationScheduleTest {

    @Test
    @DisplayName("mealSlot null이면 NPE")
    void nullMealSlot_throws() {
        assertThatThrownBy(() -> new MedicationSchedule(
                1L, null, BigDecimal.ONE, DosageUnit.TABLET, "DAILY", LocalDate.now(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("update에 mealSlot null이면 기존 값 유지하고 dosageQuantity는 갱신")
    void update_keepsMealSlotWhenNull() {
        final MedicationSchedule schedule = new MedicationSchedule(
                1L, MealSlot.BREAKFAST, BigDecimal.ONE, DosageUnit.TABLET, "DAILY", LocalDate.now(), null);

        schedule.update(null, new BigDecimal("2"), null, null, null, null);

        assertThat(schedule.getMealSlot()).isEqualTo(MealSlot.BREAKFAST);
        assertThat(schedule.getDosageQuantity()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(schedule.getDosageUnit()).isEqualTo(DosageUnit.TABLET);
    }

    @Test
    @DisplayName("update로 슬롯 변경 가능")
    void update_changesMealSlot() {
        final MedicationSchedule schedule = new MedicationSchedule(
                1L, MealSlot.BREAKFAST, BigDecimal.ONE, DosageUnit.TABLET, "DAILY", LocalDate.now(), null);

        schedule.update(MealSlot.DINNER, null, null, null, null, null);

        assertThat(schedule.getMealSlot()).isEqualTo(MealSlot.DINNER);
    }
}
