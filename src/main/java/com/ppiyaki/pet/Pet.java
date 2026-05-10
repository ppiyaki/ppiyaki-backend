package com.ppiyaki.pet;

import com.ppiyaki.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "pets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pet extends BaseTimeEntity {

    private static final int RESET_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point", nullable = false)
    private long point;

    @Column(name = "streak", nullable = false)
    private int streak;

    @Enumerated(EnumType.STRING)
    @Column(name = "highest_stage", nullable = false)
    private PetStage highestStage;

    @Column(name = "last_taken_date")
    private LocalDate lastTakenDate;

    Pet(final long point) {
        this.point = point;
        this.streak = 0;
        this.highestStage = PetStage.EGG;
    }

    public static Pet create() {
        return new Pet(0L);
    }

    public void addPoint(final long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.point += amount;
    }

    public int getLevel() {
        return (int) Math.floor(Math.sqrt(this.point / 10.0));
    }

    public void incrementStreak(final LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        if (this.lastTakenDate != null && this.lastTakenDate.equals(date)) {
            return;
        }
        if (this.lastTakenDate != null) {
            final long daysBetween = ChronoUnit.DAYS.between(this.lastTakenDate, date);
            if (daysBetween == 1) {
                this.streak++;
            } else {
                resetStreak();
                this.streak = 1;
            }
        } else {
            this.streak++;
        }
        this.lastTakenDate = date;

        final PetStage currentStage = PetStage.fromStreak(this.streak);
        if (currentStage.ordinal() > this.highestStage.ordinal()) {
            this.highestStage = currentStage;
        }
    }

    public void checkAndResetIfInactive(final LocalDate today) {
        Objects.requireNonNull(today, "today must not be null");
        if (this.lastTakenDate != null
                && ChronoUnit.DAYS.between(this.lastTakenDate, today) >= RESET_DAYS) {
            resetStreak();
        }
    }

    void resetStreak() {
        this.streak = 0;
        this.highestStage = PetStage.EGG;
    }
}
