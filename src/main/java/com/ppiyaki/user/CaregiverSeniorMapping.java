package com.ppiyaki.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import com.ppiyaki.common.entity.BaseTimeEntity;

@Entity
@Getter
@Table(name = "caregiver_senior_mappings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaregiverSeniorMapping extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_id")
    private Long seniorId;

    @Column(name = "caregiver_id")
    private Long caregiverId;

    @Column(name = "invite_code")
    private String inviteCode;
    
    public CaregiverSeniorMapping(
            final Long seniorId, 
            final Long caregiverId, 
            final String inviteCode
    ) {
        this.seniorId = seniorId;
        this.caregiverId = caregiverId;
        this.inviteCode = inviteCode;
    }
}
