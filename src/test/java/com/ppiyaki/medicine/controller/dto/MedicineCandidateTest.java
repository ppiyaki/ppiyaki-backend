package com.ppiyaki.medicine.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MedicineCandidateTest {

    private static Map<String, Object> itemWithMaterial(final String materialName) {
        final Map<String, Object> item = new HashMap<>();
        item.put("ITEM_SEQ", "1");
        item.put("ITEM_NAME", "테스트정");
        item.put("MATERIAL_NAME", materialName);
        return item;
    }

    @Test
    @DisplayName("단일 성분은 성분명과 분량·단위만 남긴다 (규격·빈 칸 제거)")
    void formatSingleIngredient() {
        // given
        final Map<String, Object> item = itemWithMaterial("아세트아미노펜,,500,밀리그램,USP,");

        // when
        final MedicineCandidate candidate = MedicineCandidate.fromMfdsItem(item);

        // then
        assertThat(candidate.mainIngr()).isEqualTo("아세트아미노펜 500밀리그램");
    }

    @Test
    @DisplayName("분량이 비어 있으면 성분명만 남기고 연속 쉼표(,,,)를 만들지 않는다")
    void formatIngredientWithoutAmount() {
        // given
        final Map<String, Object> item = itemWithMaterial(
                "아세트아미노펜,,,밀리그램,USP,/슈도에페드린염산염,,,밀리그램,USP,");

        // when
        final MedicineCandidate candidate = MedicineCandidate.fromMfdsItem(item);

        // then
        assertThat(candidate.mainIngr()).isEqualTo("아세트아미노펜, 슈도에페드린염산염");
    }

    @Test
    @DisplayName("복합 성분은 쉼표로 구분하고 소수점 뒤 0은 정리한다")
    void formatMultipleIngredients() {
        // given
        final Map<String, Object> item = itemWithMaterial(
                "로사르탄칼륨,1511.09,100.00,밀리그램,EP,/암로디핀캄실산염,1511.09,7.84,밀리그램,별첨규격(전과동),");

        // when
        final MedicineCandidate candidate = MedicineCandidate.fromMfdsItem(item);

        // then
        assertThat(candidate.mainIngr()).isEqualTo("로사르탄칼륨 100밀리그램, 암로디핀캄실산염 7.84밀리그램");
    }

    @Test
    @DisplayName("규격만 다른 중복 성분은 한 번만 표시한다")
    void deduplicatesIdenticalIngredients() {
        // given
        final Map<String, Object> item = itemWithMaterial(
                "로사르탄칼륨,1335.29,50.00,밀리그램,EP,/로사르탄칼륨,1412.04,50.00,밀리그램,EP,");

        // when
        final MedicineCandidate candidate = MedicineCandidate.fromMfdsItem(item);

        // then
        assertThat(candidate.mainIngr()).isEqualTo("로사르탄칼륨 50밀리그램");
    }

    @Test
    @DisplayName("MATERIAL_NAME 이 없으면 주성분은 null 이다")
    void nullMaterialReturnsNull() {
        // given
        final Map<String, Object> item = itemWithMaterial(null);

        // when
        final MedicineCandidate candidate = MedicineCandidate.fromMfdsItem(item);

        // then
        assertThat(candidate.mainIngr()).isNull();
    }
}
