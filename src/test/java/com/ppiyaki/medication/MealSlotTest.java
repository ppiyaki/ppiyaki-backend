package com.ppiyaki.medication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ppiyaki.user.User;
import java.lang.reflect.Field;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MealSlot.resolveTime: 슬롯별로 시니어 mealTime을 매핑한다")
class MealSlotTest {

    @Test
    @DisplayName("BREAKFAST → user.breakfastTime")
    void breakfast_returnsBreakfastTime() throws Exception {
        final User user = userWith(LocalTime.of(7, 30), LocalTime.of(12, 0), LocalTime.of(18, 0));

        assertThat(MealSlot.BREAKFAST.resolveTime(user)).isEqualTo(LocalTime.of(7, 30));
    }

    @Test
    @DisplayName("LUNCH → user.lunchTime")
    void lunch_returnsLunchTime() throws Exception {
        final User user = userWith(LocalTime.of(7, 30), LocalTime.of(12, 0), LocalTime.of(18, 0));

        assertThat(MealSlot.LUNCH.resolveTime(user)).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("DINNER → user.dinnerTime")
    void dinner_returnsDinnerTime() throws Exception {
        final User user = userWith(LocalTime.of(7, 30), LocalTime.of(12, 0), LocalTime.of(18, 0));

        assertThat(MealSlot.DINNER.resolveTime(user)).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("user.lunchTime이 null이면 LUNCH는 null 반환")
    void nullMealTime_returnsNull() throws Exception {
        final User user = userWith(LocalTime.of(7, 30), null, LocalTime.of(18, 0));

        assertThat(MealSlot.LUNCH.resolveTime(user)).isNull();
    }

    @Test
    @DisplayName("user가 null이면 NPE")
    void nullUser_throws() {
        assertThatThrownBy(() -> MealSlot.BREAKFAST.resolveTime(null))
                .isInstanceOf(NullPointerException.class);
    }

    private User userWith(
            final LocalTime breakfast,
            final LocalTime lunch,
            final LocalTime dinner
    ) throws Exception {
        final var ctor = User.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        final User user = ctor.newInstance();
        setField(user, "breakfastTime", breakfast);
        setField(user, "lunchTime", lunch);
        setField(user, "dinnerTime", dinner);
        return user;
    }

    private static void setField(final Object target, final String fieldName, final Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                final Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (final NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
