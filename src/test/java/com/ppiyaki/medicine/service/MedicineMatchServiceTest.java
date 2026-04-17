package com.ppiyaki.medicine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ppiyaki.medicine.controller.dto.MedicineCandidate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MedicineMatchServiceTest {

    private MedicineSearchService searchService;
    private MedicineMatchService matchService;

    @BeforeEach
    void setUp() {
        searchService = mock(MedicineSearchService.class);
        matchService = new MedicineMatchService(searchService);
    }

    @Test
    @DisplayName("이름이 정확히 일치하면 EXACT를 반환한다")
    void match_exactMatch_returnsExact() {
        // given
        final MedicineCandidate candidate = new MedicineCandidate(
                "200001", "타이레놀정500밀리그램", "한국존슨앤드존슨",
                "아세트아미노펜", "정제", "일반", "해열진통소염제");
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of(candidate));

        // when
        final MatchResult result = matchService.match("타이레놀정500밀리그램", Optional.empty());

        // then
        assertThat(result.matchType()).isEqualTo(MatchType.EXACT);
        assertThat(result.recommended()).isPresent();
        assertThat(result.recommended().get().itemName()).isEqualTo("타이레놀정500밀리그램");
    }

    @Test
    @DisplayName("이름이 일치하지 않으면 CANDIDATES를 반환한다")
    void match_noExactMatch_returnsCandidates() {
        // given
        final MedicineCandidate candidate = new MedicineCandidate(
                "200001", "타이레놀이알서방정", "한국존슨앤드존슨",
                "아세트아미노펜", "정제", "일반", "해열진통소염제");
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of(candidate));

        // when
        final MatchResult result = matchService.match("타이레놀정500mg", Optional.empty());

        // then
        assertThat(result.matchType()).isEqualTo(MatchType.CANDIDATES);
        assertThat(result.recommended()).isEmpty();
        assertThat(result.candidates()).hasSize(1);
    }

    @Test
    @DisplayName("검색 결과가 없고 성분명으로도 없으면 NO_MATCH를 반환한다")
    void match_noResults_returnsNoMatch() {
        // given
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of());

        // when
        final MatchResult result = matchService.match("존재하지않는약", Optional.empty());

        // then
        assertThat(result.matchType()).isEqualTo(MatchType.NO_MATCH);
        assertThat(result.recommended()).isEmpty();
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    @DisplayName("이름 검색 실패 시 성분명으로 재검색하여 CANDIDATES를 반환한다")
    void match_ingredientFallback_returnsCandidates() {
        // given
        final MedicineCandidate ingredientCandidate = new MedicineCandidate(
                "300001", "아스피린장용정100mg", "바이엘코리아",
                "아세틸살리실산", "장용정", "일반", "해열진통소염제");
        when(searchService.search(anyString(), anyInt()))
                .thenReturn(List.of())
                .thenReturn(List.of(ingredientCandidate));

        // when
        final MatchResult result = matchService.match("알수없는약", Optional.of("아세틸살리실산"));

        // then
        assertThat(result.matchType()).isEqualTo(MatchType.CANDIDATES);
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).mainIngr()).isEqualTo("아세틸살리실산");
    }

    @Test
    @DisplayName("괄호가 포함된 약명도 정규화하여 정확히 매칭한다")
    void match_nameWithParentheses_normalizesAndMatches() {
        // given
        final MedicineCandidate candidate = new MedicineCandidate(
                "400001", "아목시실린캡슐500mg", "종근당",
                "아목시실린", "캡슐", "전문", "항생제");
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of(candidate));

        // when
        final MatchResult result = matchService.match("아목시실린캡슐500mg(종근당)", Optional.empty());

        // then
        assertThat(result.matchType()).isEqualTo(MatchType.EXACT);
    }
}
