package com.ppiyaki.medicine;

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
@Table(name = "medicines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medicine extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prescription_id")
    private Long prescriptionId;

    @Column(name = "name")
    private String name;

    @Column(name = "total_amount")
    private Integer totalAmount;

    @Column(name = "remaining_amount")
    private Integer remainingAmount;

    @Column(name = "dur_warning_text")
    private String durWarningText;
    
    public Medicine(
            final Long prescriptionId, 
            final String name, 
            final Integer totalAmount, 
            final Integer remainingAmount, 
            final String durWarningText
    ) {
        this.prescriptionId = prescriptionId;
        this.name = name;
        this.totalAmount = totalAmount;
        this.remainingAmount = remainingAmount;
        this.durWarningText = durWarningText;
    }
}
