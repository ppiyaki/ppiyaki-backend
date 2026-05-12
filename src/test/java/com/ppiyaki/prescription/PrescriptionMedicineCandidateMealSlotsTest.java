package com.ppiyaki.prescription;

import static org.assertj.core.api.Assertions.assertThat;

import com.ppiyaki.medication.MealSlot;
import com.ppiyaki.medicine.service.MatchType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PrescriptionMedicineCandidate mealSlots 도메인 동작")
class PrescriptionMedicineCandidateMealSlotsTest {

    @Test
    @DisplayName("생성 시 suggestedMealSlots는 CSV로 직렬화되고, 리스트 게터로 복원된다")
    void constructor_persistsSuggestedSlotsAsCsv() {
        final PrescriptionMedicineCandidate candidate = new PrescriptionMedicineCandidate(
                1L, "raw", "타이레놀정", java.math.BigDecimal.ONE, com.ppiyaki.medication.DosageUnit.TABLET, "1일 3회 식후",
                "ITEM-1", "타이레놀", MatchType.EXACT, "matched",
                List.of(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER)
        );

        assertThat(candidate.getSuggestedMealSlots()).isEqualTo("BREAKFAST,LUNCH,DINNER");
        assertThat(candidate.getSuggestedMealSlotsList())
                .containsExactly(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER);
        assertThat(candidate.getConfirmedMealSlotsList()).isEmpty();
    }

    @Test
    @DisplayName("suggestedMealSlots null/empty → CSV는 null, 리스트 게터는 빈 리스트")
    void constructor_emptySuggestedSlots() {
        final PrescriptionMedicineCandidate candidate = new PrescriptionMedicineCandidate(
                1L, "raw", "약", java.math.BigDecimal.ONE, com.ppiyaki.medication.DosageUnit.TABLET, "취침 전",
                null, null, MatchType.NO_MATCH, "no match",
                List.of()
        );

        assertThat(candidate.getSuggestedMealSlots()).isNull();
        assertThat(candidate.getSuggestedMealSlotsList()).isEmpty();
    }

    @Test
    @DisplayName("updateConfirmedMealSlots: 리스트 → CSV 저장")
    void updateConfirmedMealSlots_persistsCsv() {
        final PrescriptionMedicineCandidate candidate = new PrescriptionMedicineCandidate(
                1L, "raw", "약", java.math.BigDecimal.ONE, com.ppiyaki.medication.DosageUnit.TABLET, "1일 2회",
                null, null, MatchType.NO_MATCH, null,
                List.of(MealSlot.BREAKFAST, MealSlot.DINNER)
        );

        candidate.updateConfirmedMealSlots(List.of(MealSlot.LUNCH, MealSlot.DINNER));

        assertThat(candidate.getConfirmedMealSlots()).isEqualTo("LUNCH,DINNER");
        assertThat(candidate.getConfirmedMealSlotsList())
                .containsExactly(MealSlot.LUNCH, MealSlot.DINNER);
    }

    @Test
    @DisplayName("updateConfirmedMealSlots: 빈 리스트 → null로 초기화")
    void updateConfirmedMealSlots_emptyClearsField() {
        final PrescriptionMedicineCandidate candidate = new PrescriptionMedicineCandidate(
                1L, "raw", "약", java.math.BigDecimal.ONE, com.ppiyaki.medication.DosageUnit.TABLET, "1일 2회",
                null, null, MatchType.NO_MATCH, null,
                List.of(MealSlot.BREAKFAST)
        );
        candidate.updateConfirmedMealSlots(List.of(MealSlot.BREAKFAST));
        assertThat(candidate.getConfirmedMealSlots()).isEqualTo("BREAKFAST");

        candidate.updateConfirmedMealSlots(List.of());

        assertThat(candidate.getConfirmedMealSlots()).isNull();
        assertThat(candidate.getConfirmedMealSlotsList()).isEmpty();
    }

    @Test
    @DisplayName("manualAdd factory: suggestedMealSlots는 비어있음")
    void manualAdd_emptySuggestedSlots() {
        final PrescriptionMedicineCandidate candidate = PrescriptionMedicineCandidate.manualAdd(
                1L, "ITEM-1", "타이레놀정", java.math.BigDecimal.ONE, com.ppiyaki.medication.DosageUnit.TABLET, "1일 3회"
        );

        assertThat(candidate.getSuggestedMealSlots()).isNull();
        assertThat(candidate.getSuggestedMealSlotsList()).isEmpty();
        assertThat(candidate.getConfirmedMealSlotsList()).isEmpty();
        assertThat(candidate.getCaregiverDecision()).isEqualTo(CaregiverDecision.ACCEPTED);
    }
}
