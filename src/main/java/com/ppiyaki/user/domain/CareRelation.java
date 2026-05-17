package com.ppiyaki.user.domain;

import com.ppiyaki.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
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

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static CareRelation createLinked(final Long seniorId, final Long caregiverId) {
        Objects.requireNonNull(seniorId, "seniorId must not be null");
        Objects.requireNonNull(caregiverId, "caregiverId must not be null");
        final CareRelation careRelation = new CareRelation();
        careRelation.seniorId = seniorId;
        careRelation.caregiverId = caregiverId;
        return careRelation;
    }

    public void softDelete(final LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isActive() {
        return this.deletedAt == null;
    }
}
