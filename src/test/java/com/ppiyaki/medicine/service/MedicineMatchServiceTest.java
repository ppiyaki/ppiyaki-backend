package com.ppiyaki.medicine.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MedicineMatchServiceTest {

    @Nested
    @DisplayName("normalize")
    class NormalizeTest {

        @Test
        @DisplayName("공백·괄호·하이픈 제거")
        void removesWhitespaceAndBrackets() {
            assertThat(MedicineMatchService.normalize("타이레놀정 500mg (아세트아미노펜)"))
                    .isEqualTo("타이레놀정500mg아세트아미노펜");
        }

        @Test
        @DisplayName("밀리그람 → mg 변환")
        void convertsMilligrams() {
            assertThat(MedicineMatchService.normalize("타이레놀정500밀리그람"))
                    .isEqualTo("타이레놀정500mg");
        }

        @Test
        @DisplayName("null은 빈 문자열")
        void nullReturnsEmpty() {
            assertThat(MedicineMatchService.normalize(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("levenshteinDistance")
    class LevenshteinTest {

        @Test
        @DisplayName("동일 문자열 거리 0")
        void identicalStringsZero() {
            assertThat(MedicineMatchService.levenshteinDistance("이부프로펜", "이부프로펜")).isZero();
        }

        @Test
        @DisplayName("1글자 차이 거리 1")
        void oneCharDifference() {
            assertThat(MedicineMatchService.levenshteinDistance("이부프로펜", "이부프로멘")).isEqualTo(1);
        }

        @Test
        @DisplayName("완전히 다른 문자열")
        void completelyDifferent() {
            assertThat(MedicineMatchService.levenshteinDistance("타이레놀", "아스피린"))
                    .isGreaterThan(2);
        }
    }

    @Nested
    @DisplayName("levenshteinSimilarity")
    class SimilarityTest {

        @Test
        @DisplayName("동일 문자열 유사도 1.0")
        void identicalIsOne() {
            assertThat(MedicineMatchService.levenshteinSimilarity("타이레놀", "타이레놀"))
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("1글자 차이면 0.8 이상")
        void oneCharHighSimilarity() {
            final double sim = MedicineMatchService.levenshteinSimilarity("이부프로펜", "이부프로멘");
            assertThat(sim).isGreaterThanOrEqualTo(0.8);
        }

        @Test
        @DisplayName("빈 문자열끼리 유사도 1.0")
        void emptyStringsAreIdentical() {
            assertThat(MedicineMatchService.levenshteinSimilarity("", "")).isEqualTo(1.0);
        }
    }
}
