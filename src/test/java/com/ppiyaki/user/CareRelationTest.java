package com.ppiyaki.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CareRelationTest {

    @Test
    @DisplayName("createLinked로 생성하면 seniorId, caregiverId가 세팅되고 isActive는 true다")
    void createLinked_setsFieldsAndActive() {
        // given & when
        final CareRelation relation = CareRelation.createLinked(1L, 2L);

        // then
        assertThat(relation.getSeniorId()).isEqualTo(1L);
        assertThat(relation.getCaregiverId()).isEqualTo(2L);
        assertThat(relation.isActive()).isTrue();
        assertThat(relation.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("softDelete 호출하면 deletedAt이 세팅되고 isActive는 false가 된다")
    void softDelete_called_isActiveBecomesFalse() {
        // given
        final CareRelation relation = CareRelation.createLinked(1L, 2L);
        final LocalDateTime now = LocalDateTime.of(2026, 4, 9, 12, 0);

        // when
        relation.softDelete(now);

        // then
        assertThat(relation.getDeletedAt()).isEqualTo(now);
        assertThat(relation.isActive()).isFalse();
    }
}
