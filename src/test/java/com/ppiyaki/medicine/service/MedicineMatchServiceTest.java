package com.ppiyaki.medicine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ppiyaki.medicine.controller.dto.MedicineCandidate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicineMatchServiceTest {

    @Mock
    private MedicineSearchService searchService;

    @InjectMocks
    private MedicineMatchService matchService;

    private static MedicineCandidate candidate(final String itemName) {
        return new MedicineCandidate("SEQ001", itemName, "제약사", null, null, null, null);
    }

    @Nested
    @DisplayName("match")
    class MatchTest {

        @Test
        @DisplayName("정확 일치 시 EXACT 반환")
        void exactMatch() {
            when(searchService.search(eq("가스모틴정"), anyInt()))
                    .thenReturn(List.of(candidate("가스모틴정5밀리그램(모사프리드시트르산염수화물)")));

            final MatchResult result = matchService.match(
                    "가스모틴정5밀리그람", Optional.empty());

            assertThat(result.matchType()).isEqualTo(MatchType.EXACT);
            assertThat(result.recommended()).isPresent();
        }

        @Test
        @DisplayName("후보 있으나 정확 일치 없으면 CANDIDATES 반환")
        void candidatesMatch() {
            when(searchService.search(eq("세파클러캡슐"), anyInt()))
                    .thenReturn(List.of(
                            candidate("유한세파클러캡슐(세파클러수화물)"),
                            candidate("보령세파클러캡슐(세파클러수화물)")
                    ));

            final MatchResult result = matchService.match(
                    "세파클러캡슐250밀리그람", Optional.empty());

            assertThat(result.matchType()).isEqualTo(MatchType.CANDIDATES);
            assertThat(result.recommended()).isEmpty();
            assertThat(result.candidates()).hasSize(2);
        }

        @Test
        @DisplayName("검색 0건이면 NO_MATCH 반환")
        void noMatch() {
            when(searchService.search(eq("없는약정"), anyInt()))
                    .thenReturn(List.of());

            final MatchResult result = matchService.match(
                    "없는약정100밀리그람", Optional.empty());

            assertThat(result.matchType()).isEqualTo(MatchType.NO_MATCH);
            assertThat(result.candidates()).isEmpty();
        }

        @Test
        @DisplayName("name 검색 0건 + ingredientName으로 재검색 성공 시 CANDIDATES")
        void ingredientFallback() {
            when(searchService.search(eq("플라빅스정"), anyInt()))
                    .thenReturn(List.of());
            when(searchService.search(eq("클로피도그렐"), anyInt()))
                    .thenReturn(List.of(
                            candidate("크라빅스정(클로피도그렐황산수소염)"),
                            candidate("에라빅스정(클로피도그렐황산수소염)")
                    ));

            final MatchResult result = matchService.match(
                    "플라빅스정75밀리그람", Optional.of("클로피도그렐"));

            assertThat(result.matchType()).isEqualTo(MatchType.CANDIDATES);
            assertThat(result.candidates()).hasSize(2);
            assertThat(result.reason()).contains("성분명");
        }

        @Test
        @DisplayName("name 검색 0건 + ingredientName도 없으면 NO_MATCH")
        void noIngredientNoMatch() {
            when(searchService.search(eq("플라빅스정"), anyInt()))
                    .thenReturn(List.of());

            final MatchResult result = matchService.match(
                    "플라빅스정75밀리그람", Optional.empty());

            assertThat(result.matchType()).isEqualTo(MatchType.NO_MATCH);
        }
    }
}
