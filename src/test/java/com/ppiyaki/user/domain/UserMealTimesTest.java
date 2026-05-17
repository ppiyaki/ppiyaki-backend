package com.ppiyaki.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("User.mealTimes")
class UserMealTimesTest {

    @Test
    @DisplayName("신규 User는 식사 시간 3개가 모두 null이다")
    void 신규_유저_mealTimes_null() {
        // when
        final User user = newUser();

        // then
        assertThat(user.getBreakfastTime()).isNull();
        assertThat(user.getLunchTime()).isNull();
        assertThat(user.getDinnerTime()).isNull();
    }

    @Test
    @DisplayName("updateMealTimes로 3개 시각을 한 번에 설정할 수 있다")
    void updateMealTimes_정상() {
        // given
        final User user = newUser();
        final LocalTime breakfast = LocalTime.of(8, 0);
        final LocalTime lunch = LocalTime.of(12, 30);
        final LocalTime dinner = LocalTime.of(18, 30);

        // when
        user.updateMealTimes(breakfast, lunch, dinner);

        // then
        assertThat(user.getBreakfastTime()).isEqualTo(breakfast);
        assertThat(user.getLunchTime()).isEqualTo(lunch);
        assertThat(user.getDinnerTime()).isEqualTo(dinner);
    }

    @Test
    @DisplayName("updateMealTimes는 breakfast가 null이면 NPE")
    void updateMealTimes_breakfast_null_거부() {
        // given
        final User user = newUser();

        // when & then
        assertThatThrownBy(() -> user.updateMealTimes(null, LocalTime.of(12, 0), LocalTime.of(18, 0)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("breakfastTime");
    }

    @Test
    @DisplayName("updateMealTimes는 lunch가 null이면 NPE")
    void updateMealTimes_lunch_null_거부() {
        // given
        final User user = newUser();

        // when & then
        assertThatThrownBy(() -> user.updateMealTimes(LocalTime.of(8, 0), null, LocalTime.of(18, 0)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("lunchTime");
    }

    @Test
    @DisplayName("updateMealTimes는 dinner가 null이면 NPE")
    void updateMealTimes_dinner_null_거부() {
        // given
        final User user = newUser();

        // when & then
        assertThatThrownBy(() -> user.updateMealTimes(LocalTime.of(8, 0), LocalTime.of(12, 0), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dinnerTime");
    }

    @Test
    @DisplayName("updateMealTimes는 호출 시마다 값을 덮어쓴다")
    void updateMealTimes_가역() {
        // given
        final User user = newUser();
        user.updateMealTimes(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(18, 0));

        // when
        user.updateMealTimes(LocalTime.of(7, 30), LocalTime.of(13, 0), LocalTime.of(19, 30));

        // then
        assertThat(user.getBreakfastTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(user.getLunchTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(user.getDinnerTime()).isEqualTo(LocalTime.of(19, 30));
    }

    private User newUser() {
        return new User(
                "loginid",
                "password",
                UserRole.SENIOR,
                AuthProvider.INVITE_ONLY,
                "테스트유저",
                Gender.UNKNOWN,
                LocalDate.of(1950, 1, 1),
                null
        );
    }
}
