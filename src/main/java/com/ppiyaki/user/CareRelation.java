package com.ppiyaki.user;

import com.ppiyaki.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "care_relations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareRelation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_id")
    private Long seniorId;

    @Column(name = "caregiver_id")
    private Long caregiverId;

    @Column(name = "invite_code")
    private String inviteCode;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public CareRelation(final Long seniorId, final Long caregiverId, final String inviteCode) {
        this.seniorId = seniorId;
        this.caregiverId = caregiverId;
        this.inviteCode = inviteCode;
    }

    public void softDelete(final LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isActive() {
        return this.deletedAt == null;
    }
}
