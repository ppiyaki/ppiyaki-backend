package com.ppiyaki.user;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "users")
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

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "pet")
    private Long pet;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_mode", nullable = false)
    private CareMode careMode = CareMode.MANAGED;

    public User(
            final String loginId,
            final String password,
            final UserRole role,
            final String nickname,
            final Gender gender,
            final LocalDate dob,
            final Long pet
    ) {
        this.loginId = loginId;
        this.password = password;
        this.role = role;
        this.nickname = nickname;
        this.gender = gender;
        this.dob = dob;
        this.pet = pet;
        this.careMode = CareMode.MANAGED;
    }

    public void changeCareMode(final CareMode careMode) {
        this.careMode = careMode;
    }
}
