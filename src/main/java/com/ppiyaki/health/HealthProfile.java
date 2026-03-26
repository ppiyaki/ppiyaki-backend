package com.ppiyaki.health;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import com.ppiyaki.common.entity.CreatedTimeEntity;

@Entity
@Getter
@Table(name = "health_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthProfile extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_id")
    private Long seniorId;

    @Column(name = "diet_habits")
    private String dietHabits;

    @Column(name = "allergies")
    private String allergies;

    @Column(name = "smoking_status")
    private Boolean smokingStatus;

    @Column(name = "drinking_status")
    private Boolean drinkingStatus;
    
    public HealthProfile(
            final Long seniorId, 
            final String dietHabits, 
            final String allergies, 
            final Boolean smokingStatus, 
            final Boolean drinkingStatus
    ) {
        this.seniorId = seniorId;
        this.dietHabits = dietHabits;
        this.allergies = allergies;
        this.smokingStatus = smokingStatus;
        this.drinkingStatus = drinkingStatus;
    }
}
