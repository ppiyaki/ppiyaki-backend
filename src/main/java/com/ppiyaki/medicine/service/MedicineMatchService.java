package com.ppiyaki.medicine.service;

import com.ppiyaki.medicine.controller.dto.MedicineCandidate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class MedicineMatchService {

    private static final Logger log = LoggerFactory.getLogger(MedicineMatchService.class);

    private final MedicineSearchService searchService;

    public MedicineMatchService(final MedicineSearchService searchService) {
        this.searchService = searchService;
    }

    private String normalize(final String text) {
        if (text == null) {
            return "";
        }
        return text.strip()
                .replaceAll("[\\s()\\[\\]\\-]", "")
                .replaceAll("밀리그람|밀리그램", "mg")
                .replaceAll("그람|그램", "g")
                .replaceAll("밀리리터", "ml")
                .toLowerCase();
    }

    private String normalizeForComparison(final String text) {
        if (text == null) {
            return "";
        }
        final String withoutParenthesized = text.replaceAll("[\\(（][^\\)）]*[\\)）]", "");
        return normalize(withoutParenthesized);
    }

    private String extractSearchQuery(final String name) {
        final String withoutParenthesized = name.replaceAll("[\\(（][^\\)）]*[\\)）]", "");
        final String cleaned = normalize(withoutParenthesized);
        final String nameOnly = cleaned.replaceAll("[0-9]+(\\.[0-9]+)?(mg|g|ml|%|mcg|iu)?.*$", "");
        return nameOnly.isEmpty() ? cleaned : nameOnly;
    }

    public MatchResult match(
            final String name,
            final Optional<String> ingredientName
    ) {
        final String searchQuery = extractSearchQuery(name);
        final List<MedicineCandidate> candidates = searchService.search(searchQuery, 20);

        if (!candidates.isEmpty()) {
            return classify(name, candidates);
        }

        if (ingredientName.isPresent() && !ingredientName.get().isBlank()) {
            final String ingredientQuery = normalize(ingredientName.get());
            final List<MedicineCandidate> ingredientCandidates = searchService.search(ingredientQuery, 20);

            if (!ingredientCandidates.isEmpty()) {
                log.info("Ingredient search hit: '{}' → ingredient '{}' → {} results",
                        name, ingredientQuery, ingredientCandidates.size());
                final List<MedicineCandidate> limited = ingredientCandidates.stream().limit(5).toList();
                return new MatchResult(MatchType.CANDIDATES, Optional.empty(), limited,
                        "성분명 '" + ingredientName.get() + "'(으)로 동일 성분 약물 "
                                + limited.size() + "개 발견. 수동 선택 필요.");
            }
        }

        log.info("No match: '{}'", name);
        return new MatchResult(MatchType.NO_MATCH, Optional.empty(), List.of(),
                "유사한 약물을 찾지 못함");
    }

    private MatchResult classify(final String name, final List<MedicineCandidate> candidates) {
        final String normalized = normalizeForComparison(name);

        for (final MedicineCandidate candidate : candidates) {
            if (normalizeForComparison(candidate.itemName()).equals(normalized)) {
                log.info("Exact match: '{}' → '{}'", name, candidate.itemName());
                return new MatchResult(MatchType.EXACT, Optional.of(candidate), List.of(),
                        "정확 일치");
            }
        }

        final List<MedicineCandidate> limited = candidates.stream().limit(5).toList();
        return new MatchResult(MatchType.CANDIDATES, Optional.empty(), limited,
                "후보 약물 " + limited.size() + "개 발견. 수동 선택 필요.");
    }
}
