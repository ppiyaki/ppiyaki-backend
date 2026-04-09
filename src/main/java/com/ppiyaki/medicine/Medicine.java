package com.ppiyaki.medicine;

import com.ppiyaki.common.entity.CreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "medicines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medicine extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

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
            final Long ownerId,
            final Long prescriptionId,
            final String name,
            final Integer totalAmount,
            final Integer remainingAmount,
            final String durWarningText
    ) {
        this.ownerId = ownerId;
        this.prescriptionId = prescriptionId;
        this.name = name;
        this.totalAmount = totalAmount;
        this.remainingAmount = remainingAmount;
        this.durWarningText = durWarningText;
    }
}
