package com.ppiyaki.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("User.timezone")
class UserTimezoneTest {

    @Test
    @DisplayName("신규 User의 타임존 기본값은 Asia/Seoul이다")
    void 신규_유저_타임존_기본값() {
        // when
        final User user = newUser();

        // then
        assertThat(user.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(user.getZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("updateTimezone으로 타임존을 변경할 수 있다")
    void updateTimezone_정상() {
        // given
        final User user = newUser();

        // when
        user.updateTimezone("America/New_York");

        // then
        assertThat(user.getTimezone()).isEqualTo("America/New_York");
        assertThat(user.getZoneId()).isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    @DisplayName("updateTimezone은 null이면 NPE")
    void updateTimezone_null_거부() {
        // given
        final User user = newUser();

        // when & then
        assertThatThrownBy(() -> user.updateTimezone(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("timezone");
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
