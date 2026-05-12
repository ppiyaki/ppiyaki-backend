package com.ppiyaki.prescription;

import com.ppiyaki.common.entity.CreatedTimeEntity;
import com.ppiyaki.medication.MealSlot;
import com.ppiyaki.medicine.service.MatchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @Column(name = "extracted_dosage_quantity", precision = 5, scale = 2)
    private BigDecimal extractedDosageQuantity;

    @Column(name = "extracted_dosage_unit", length = 16)
    private String extractedDosageUnit;

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

    @Column(name = "suggested_meal_slots", length = 64)
    private String suggestedMealSlots;

    @Column(name = "confirmed_meal_slots", length = 64)
    private String confirmedMealSlots;

    private static final String MANUAL_ADD_REASON = "보호자 수동 추가";
    private static final String MANUAL_ADD_RAW_TEXT = "보호자 수동 추가";

    public PrescriptionMedicineCandidate(
            final Long prescriptionId,
            final String ocrRawText,
            final String extractedName,
            final String extractedDosage,
            final String extractedSchedule,
            final String matchedItemSeq,
            final String matchedItemName,
            final MatchType matchType,
            final String matchReason,
            final List<MealSlot> suggestedMealSlots
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
        this.suggestedMealSlots = MealSlot.toCsv(suggestedMealSlots);
    }

    public static PrescriptionMedicineCandidate manualAdd(
            final Long prescriptionId,
            final String itemSeq,
            final String itemName,
            final String dosage,
            final String schedule
    ) {
        Objects.requireNonNull(itemSeq, "itemSeq must not be null");
        Objects.requireNonNull(itemName, "itemName must not be null");
        final PrescriptionMedicineCandidate candidate = new PrescriptionMedicineCandidate(
                prescriptionId,
                MANUAL_ADD_RAW_TEXT,
                itemName,
                dosage,
                schedule,
                itemSeq,
                itemName,
                MatchType.EXACT,
                MANUAL_ADD_REASON,
                List.of()
        );
        candidate.caregiverDecision = CaregiverDecision.ACCEPTED;
        candidate.caregiverChosenItemSeq = itemSeq;
        candidate.reviewedAt = LocalDateTime.now();
        return candidate;
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

    public void updateConfirmedMealSlots(final List<MealSlot> slots) {
        this.confirmedMealSlots = MealSlot.toCsv(slots);
    }

    public void updateExtractedDosage(final String dosage) {
        this.extractedDosage = dosage;
    }

    public List<MealSlot> getSuggestedMealSlotsList() {
        return MealSlot.parseCsv(this.suggestedMealSlots);
    }

    public List<MealSlot> getConfirmedMealSlotsList() {
        return MealSlot.parseCsv(this.confirmedMealSlots);
    }
}
