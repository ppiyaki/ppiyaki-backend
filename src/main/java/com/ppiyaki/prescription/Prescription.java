package com.ppiyaki.prescription;

import com.ppiyaki.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "prescriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PrescriptionStatus status;

    @Column(name = "masked_image_object_key")
    private String maskedImageObjectKey;

    @Column(columnDefinition = "TEXT", name = "failure_reason")
    private String failureReason;

    public Prescription(final Long ownerId) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.status = PrescriptionStatus.PROCESSING;
    }

    public void complete(final String maskedImageObjectKey) {
        this.maskedImageObjectKey = maskedImageObjectKey;
        this.status = PrescriptionStatus.PENDING_REVIEW;
    }

    public void fail(final String reason) {
        this.failureReason = reason;
        this.status = PrescriptionStatus.PROCESSING_FAILED;
    }

    public void confirm() {
        this.status = PrescriptionStatus.CONFIRMED;
    }

    public void reject() {
        this.status = PrescriptionStatus.REJECTED;
    }
}
