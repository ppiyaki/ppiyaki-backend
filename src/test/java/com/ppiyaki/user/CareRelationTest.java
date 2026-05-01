package com.ppiyaki.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CareRelationTest {

    @Test
    @DisplayName("생성 직후 isActive는 true다")
    void createInvite_isActive_true() {
        // given
        final CareRelation relation = CareRelation.createInvite(1L, LocalDateTime.now());

        // when & then
        assertThat(relation.isActive()).isTrue();
        assertThat(relation.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("softDelete 호출하면 deletedAt이 세팅되고 isActive는 false가 된다")
    void softDelete_called_isActiveBecomesFalse() {
        // given
        final CareRelation relation = CareRelation.createInvite(1L, LocalDateTime.now());
        final LocalDateTime now = LocalDateTime.of(2026, 4, 9, 12, 0);

        // when
        relation.softDelete(now);

        // then
        assertThat(relation.getDeletedAt()).isEqualTo(now);
        assertThat(relation.isActive()).isFalse();
    }

    @Test
    @DisplayName("createInvite 후 값을 보존한다")
    void createInvite_preservesValues() {
        // given
        final CareRelation relation = CareRelation.createInvite(20L, LocalDateTime.now());

        // when & then
        assertThat(relation.getCaregiverId()).isEqualTo(20L);
        assertThat(relation.getSeniorId()).isNull();
        assertThat(relation.getInviteCode()).matches("[A-Z0-9]{6}");
        assertThat(relation.isPending()).isTrue();
    }

    @Test
    @DisplayName("acceptInvite 후 seniorId가 세팅되고 코드가 폐기된다")
    void acceptInvite_setsSeniorAndClearsCode() {
        // given
        final CareRelation relation = CareRelation.createInvite(20L, LocalDateTime.now());

        // when
        relation.acceptInvite(10L);

        // then
        assertThat(relation.getSeniorId()).isEqualTo(10L);
        assertThat(relation.getCaregiverId()).isEqualTo(20L);
        assertThat(relation.getInviteCode()).isNull();
        assertThat(relation.getExpiresAt()).isNull();
        assertThat(relation.isPending()).isFalse();
    }
}
