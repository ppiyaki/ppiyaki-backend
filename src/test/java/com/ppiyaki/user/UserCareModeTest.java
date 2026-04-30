package com.ppiyaki.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("User.careMode")
class UserCareModeTest {

    @Test
    @DisplayName("신규 User는 careMode가 MANAGED로 초기화된다")
    void 신규_유저_careMode_default_MANAGED() {
        // when
        final User user = newUser();

        // then
        assertThat(user.getCareMode()).isEqualTo(CareMode.MANAGED);
    }

    @Test
    @DisplayName("changeCareMode로 모드를 AUTONOMOUS로 변경할 수 있다")
    void careMode_변경_AUTONOMOUS() {
        // given
        final User user = newUser();

        // when
        user.changeCareMode(CareMode.AUTONOMOUS);

        // then
        assertThat(user.getCareMode()).isEqualTo(CareMode.AUTONOMOUS);
    }

    @Test
    @DisplayName("changeCareMode에 null을 전달하면 NullPointerException")
    void careMode_null_전달_실패() {
        // given
        final User user = newUser();

        // when & then
        assertThatThrownBy(() -> user.changeCareMode(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("careMode");
    }

    @Test
    @DisplayName("AUTONOMOUS로 변경한 뒤 다시 MANAGED로 되돌릴 수 있다")
    void careMode_변경_가역() {
        // given
        final User user = newUser();
        user.changeCareMode(CareMode.AUTONOMOUS);

        // when
        user.changeCareMode(CareMode.MANAGED);

        // then
        assertThat(user.getCareMode()).isEqualTo(CareMode.MANAGED);
    }

    private User newUser() {
        return new User(
                "loginid",
                "password",
                UserRole.SENIOR,
                "테스트유저",
                Gender.UNKNOWN,
                LocalDate.of(1950, 1, 1),
                null
        );
    }
}
