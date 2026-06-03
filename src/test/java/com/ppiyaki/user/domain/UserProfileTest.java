package com.ppiyaki.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("User.profile")
class UserProfileTest {

    @Test
    @DisplayName("신규 User는 프로필 사진 인덱스/objectKey가 모두 null이다")
    void 신규_유저_프로필_null() {
        // when
        final User user = newUser();

        // then
        assertThat(user.getProfileImage()).isNull();
        assertThat(user.getProfileImageObjectKey()).isNull();
    }

    @Test
    @DisplayName("updateProfileImage로 기본 프사 인덱스를 설정하면 objectKey는 비워진다")
    void updateProfileImage_인덱스_설정() {
        // given
        final User user = newUser();

        // when
        user.updateProfileImage(3, null);

        // then
        assertThat(user.getProfileImage()).isEqualTo(3);
        assertThat(user.getProfileImageObjectKey()).isNull();
    }

    @Test
    @DisplayName("updateProfileImage로 커스텀 objectKey를 설정하면 인덱스는 비워진다")
    void updateProfileImage_objectKey_설정() {
        // given
        final User user = newUser();
        user.updateProfileImage(3, null);

        // when
        user.updateProfileImage(null, "profile-image/1/uuid.jpg");

        // then
        assertThat(user.getProfileImage()).isNull();
        assertThat(user.getProfileImageObjectKey()).isEqualTo("profile-image/1/uuid.jpg");
    }

    @Test
    @DisplayName("updateProfileImage는 인덱스와 objectKey를 동시에 주면 거부한다")
    void updateProfileImage_상호배타() {
        // given
        final User user = newUser();

        // when & then
        assertThatThrownBy(() -> user.updateProfileImage(3, "profile-image/1/uuid.jpg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    @DisplayName("updateGender로 성별을 변경할 수 있다")
    void updateGender_정상() {
        // given
        final User user = newUser();

        // when
        user.updateGender(Gender.FEMALE);

        // then
        assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("updateGender는 null이면 NPE")
    void updateGender_null_거부() {
        // given
        final User user = newUser();

        // when & then
        assertThatThrownBy(() -> user.updateGender(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("gender");
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
