package com.ppiyaki.user.domain;

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
import java.time.LocalTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "users")
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", unique = true)
    private String loginId;

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private UserRole role;

    @Column(name = "nickname")
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "pet_id")
    private Long petId;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_mode", nullable = false)
    private CareMode careMode;

    @Column(name = "breakfast_time")
    private LocalTime breakfastTime;

    @Column(name = "lunch_time")
    private LocalTime lunchTime;

    @Column(name = "dinner_time")
    private LocalTime dinnerTime;

    @Column(name = "last_active_at")
    private java.time.LocalDateTime lastActiveAt;

    @Column(name = "onboarded", nullable = false)
    private boolean onboarded = false;

    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    public User(
            final String loginId,
            final String password,
            final UserRole role,
            final AuthProvider authProvider,
            final String nickname,
            final Gender gender,
            final LocalDate birthDate,
            final Long petId
    ) {
        this.loginId = loginId;
        this.password = password;
        this.role = role;
        this.authProvider = Objects.requireNonNull(authProvider, "authProvider must not be null");
        this.nickname = nickname;
        this.gender = gender;
        this.birthDate = birthDate;
        this.petId = petId;
        this.careMode = CareMode.MANAGED;
    }

    public static User createSenior(final String nickname, final LocalDate birthDate) {
        return new User(null, null, UserRole.SENIOR, AuthProvider.INVITE_ONLY,
                nickname, null, birthDate, null);
    }

    public static User createSenior(final String nickname, final Gender gender) {
        Objects.requireNonNull(nickname, "nickname must not be null");
        Objects.requireNonNull(gender, "gender must not be null");
        return new User(null, null, UserRole.SENIOR, AuthProvider.INVITE_ONLY,
                nickname, gender, null, null);
    }

    public void assignRole(final UserRole role) {
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    public void updateNickname(final String nickname) {
        this.nickname = Objects.requireNonNull(nickname, "nickname must not be null");
    }

    public void assignPet(final Long petId) {
        this.petId = Objects.requireNonNull(petId, "petId must not be null");
    }

    public void changeCareMode(final CareMode careMode) {
        this.careMode = Objects.requireNonNull(careMode, "careMode must not be null");
    }

    public void updateMealTimes(
            final LocalTime breakfastTime,
            final LocalTime lunchTime,
            final LocalTime dinnerTime
    ) {
        this.breakfastTime = Objects.requireNonNull(breakfastTime, "breakfastTime must not be null");
        this.lunchTime = Objects.requireNonNull(lunchTime, "lunchTime must not be null");
        this.dinnerTime = Objects.requireNonNull(dinnerTime, "dinnerTime must not be null");
    }

    public void touchActiveAt(final java.time.LocalDateTime now) {
        this.lastActiveAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void softDelete(final java.time.LocalDateTime now) {
        this.deletedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void completeOnboarding() {
        this.onboarded = true;
    }

    public boolean isOnboarded() {
        return this.onboarded;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
