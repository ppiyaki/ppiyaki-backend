package com.ppiyaki.medicine;

import com.ppiyaki.common.entity.CreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
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

    @Column(name = "item_seq")
    private String itemSeq;

    @Column(name = "dur_warning_text")
    private String durWarningText;

    public Medicine(
            final Long ownerId,
            final Long prescriptionId,
            final String name,
            final Integer totalAmount,
            final Integer remainingAmount,
            final String itemSeq,
            final String durWarningText
    ) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.prescriptionId = prescriptionId;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        this.remainingAmount = Objects.requireNonNull(remainingAmount, "remainingAmount must not be null");
        this.itemSeq = itemSeq;
        this.durWarningText = durWarningText;
    }

    public void update(
            final String name,
            final Integer totalAmount,
            final Integer remainingAmount,
            final String itemSeq,
            final String durWarningText
    ) {
        if (name != null) {
            this.name = name;
        }
        if (totalAmount != null) {
            this.totalAmount = totalAmount;
        }
        if (remainingAmount != null) {
            this.remainingAmount = remainingAmount;
        }
        if (itemSeq != null) {
            this.itemSeq = itemSeq;
        }
        if (durWarningText != null) {
            this.durWarningText = durWarningText;
        }
    }
}
