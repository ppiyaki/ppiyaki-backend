package com.ppiyaki.pet;

import com.ppiyaki.common.entity.CreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "badges", uniqueConstraints = {
        @UniqueConstraint(name = "uk_badges_pet_type", columnNames = {"pet_id", "badge_type"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Badge extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pet_id", nullable = false)
    private Long petId;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_type", nullable = false)
    private BadgeType badgeType;

    @Column(name = "earned_at", nullable = false)
    private LocalDateTime earnedAt;

    public Badge(final Long petId, final BadgeType badgeType) {
        this.petId = Objects.requireNonNull(petId, "petId must not be null");
        this.badgeType = Objects.requireNonNull(badgeType, "badgeType must not be null");
        this.earnedAt = LocalDateTime.now();
    }
}
