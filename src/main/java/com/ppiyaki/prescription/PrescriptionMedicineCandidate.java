package com.ppiyaki.prescription;

import com.ppiyaki.common.entity.CreatedTimeEntity;
import com.ppiyaki.medicine.service.MatchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "prescription_medicine_candidates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrescriptionMedicineCandidate extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Column(name = "ocr_raw_text")
    private String ocrRawText;

    @Column(name = "extracted_name")
    private String extractedName;

    @Column(name = "extracted_dosage")
    private String extractedDosage;

    @Column(name = "extracted_schedule")
    private String extractedSchedule;

    @Column(name = "matched_item_seq")
    private String matchedItemSeq;

    @Column(name = "matched_item_name")
    private String matchedItemName;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type")
    private MatchType matchType;

    @Column(columnDefinition = "TEXT", name = "match_reason")
    private String matchReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "caregiver_decision", nullable = false)
    private CaregiverDecision caregiverDecision;

    @Column(name = "caregiver_chosen_item_seq")
    private String caregiverChosenItemSeq;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_medicine_id")
    private Long createdMedicineId;

    public PrescriptionMedicineCandidate(
            final Long prescriptionId,
            final String ocrRawText,
            final String extractedName,
            final String extractedDosage,
            final String extractedSchedule,
            final String matchedItemSeq,
            final String matchedItemName,
            final MatchType matchType,
            final String matchReason
    ) {
        this.prescriptionId = Objects.requireNonNull(prescriptionId, "prescriptionId must not be null");
        this.ocrRawText = ocrRawText;
        this.extractedName = extractedName;
        this.extractedDosage = extractedDosage;
        this.extractedSchedule = extractedSchedule;
        this.matchedItemSeq = matchedItemSeq;
        this.matchedItemName = matchedItemName;
        this.matchType = matchType;
        this.matchReason = matchReason;
        this.caregiverDecision = CaregiverDecision.PENDING;
    }

    public void accept() {
        this.caregiverDecision = CaregiverDecision.ACCEPTED;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject() {
        this.caregiverDecision = CaregiverDecision.REJECTED;
        this.reviewedAt = LocalDateTime.now();
    }

    public void correctManually(final String chosenItemSeq) {
        this.caregiverDecision = CaregiverDecision.MANUALLY_CORRECTED;
        this.caregiverChosenItemSeq = chosenItemSeq;
        this.reviewedAt = LocalDateTime.now();
    }

    public void linkMedicine(final Long medicineId) {
        this.createdMedicineId = medicineId;
    }
}
