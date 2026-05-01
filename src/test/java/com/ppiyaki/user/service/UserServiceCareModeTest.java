package com.ppiyaki.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.user.CareMode;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.Gender;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.controller.dto.CareModeResponse;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService.updateCareMode")
class UserServiceCareModeTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CareRelationRepository careRelationRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("활성 보호자가 시니어 careMode를 AUTONOMOUS로 변경하면 성공")
    void 보호자_변경_성공() throws Exception {
        // given
        final Long seniorId = 100L;
        final Long caregiverId = 200L;
        final User senior = givenSenior(seniorId);
        when(userRepository.findById(seniorId)).thenReturn(Optional.of(senior));
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(caregiverId, seniorId))
                .thenReturn(Optional.of(new CareRelation(seniorId, caregiverId, "INVITE")));

        // when
        final CareModeResponse response = userService.updateCareMode(caregiverId, seniorId, CareMode.AUTONOMOUS);

        // then
        assertThat(response.userId()).isEqualTo(seniorId);
        assertThat(response.careMode()).isEqualTo(CareMode.AUTONOMOUS);
        assertThat(senior.getCareMode()).isEqualTo(CareMode.AUTONOMOUS);
    }

    @Test
    @DisplayName("시니어 본인이 셀프 변경 시도하면 CARE_RELATION_NOT_FOUND")
    void 시니어_셀프_변경_실패() throws Exception {
        // given
        final Long seniorId = 100L;
        final User senior = givenSenior(seniorId);
        when(userRepository.findById(seniorId)).thenReturn(Optional.of(senior));
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(seniorId, seniorId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateCareMode(seniorId, seniorId, CareMode.AUTONOMOUS))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARE_RELATION_NOT_FOUND);
        assertThat(senior.getCareMode()).isEqualTo(CareMode.MANAGED);
    }

    @Test
    @DisplayName("관계 없는 사용자가 호출하면 CARE_RELATION_NOT_FOUND")
    void 관계_없음_실패() throws Exception {
        // given
        final Long seniorId = 100L;
        final Long otherUserId = 999L;
        final User senior = givenSenior(seniorId);
        when(userRepository.findById(seniorId)).thenReturn(Optional.of(senior));
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(otherUserId, seniorId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateCareMode(otherUserId, seniorId, CareMode.AUTONOMOUS))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARE_RELATION_NOT_FOUND);
    }

    @Test
    @DisplayName("seniorId 미존재 시 USER_NOT_FOUND")
    void 시니어_미존재_실패() {
        // given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateCareMode(200L, 999L, CareMode.AUTONOMOUS))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private User givenSenior(final Long id) throws Exception {
        final User user = new User(
                "loginid",
                "password",
                UserRole.SENIOR,
                "시니어",
                Gender.UNKNOWN,
                LocalDate.of(1950, 1, 1),
                null
        );
        final Field idField = user.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
        return user;
    }
}
