package com.ppiyaki.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CareRelationTest {

    @Test
    void 생성직후_isActive는_true다() {
        CareRelation relation = new CareRelation(1L, 2L, "INVITE-CODE");

        assertThat(relation.isActive()).isTrue();
        assertThat(relation.getDeletedAt()).isNull();
    }

    @Test
    void softDelete_호출하면_deletedAt이_세팅되고_isActive는_false가된다() {
        CareRelation relation = new CareRelation(1L, 2L, "INVITE-CODE");
        LocalDateTime now = LocalDateTime.of(2026, 4, 9, 12, 0);

        relation.softDelete(now);

        assertThat(relation.getDeletedAt()).isEqualTo(now);
        assertThat(relation.isActive()).isFalse();
    }

    @Test
    void softDelete_값을_보존한다() {
        CareRelation relation = new CareRelation(10L, 20L, "CODE-XYZ");

        assertThat(relation.getSeniorId()).isEqualTo(10L);
        assertThat(relation.getCaregiverId()).isEqualTo(20L);
        assertThat(relation.getInviteCode()).isEqualTo("CODE-XYZ");
    }
}
