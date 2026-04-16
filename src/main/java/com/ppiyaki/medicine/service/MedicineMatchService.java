package com.ppiyaki.medicine.service;

import com.ppiyaki.medicine.controller.dto.MedicineCandidate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class MedicineMatchService {

    private static final Logger log = LoggerFactory.getLogger(MedicineMatchService.class);
    private static final double MANUAL_REQUIRED_THRESHOLD = 0.50;

    private final MedicineSearchService searchService;
    private final double fuzzyAutoThreshold;

    public MedicineMatchService(
            final MedicineSearchService searchService,
            @Value("${medicine.matching.fuzzy-auto-threshold:0.90}") final double fuzzyAutoThreshold
    ) {
        this.searchService = searchService;
        this.fuzzyAutoThreshold = fuzzyAutoThreshold;
    }

    static String normalize(final String text) {
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

    static double levenshteinSimilarity(final String a, final String b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        final int maxLen = Math.max(a.length(), b.length());
        return 1.0 - ((double) levenshteinDistance(a, b) / maxLen);
    }

    static int levenshteinDistance(final String a, final String b) {
        final int lenA = a.length();
        final int lenB = b.length();
        final int[][] dp = new int[lenA + 1][lenB + 1];

        for (int i = 0; i <= lenA; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= lenB; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= lenA; i++) {
            for (int j = 1; j <= lenB; j++) {
                final int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[lenA][lenB];
    }

    public MatchResult match(
            final String ocrText,
            final Optional<String> dosageHint,
            final Optional<String> formHint
    ) {
        final String normalized = normalize(ocrText);
        final List<MedicineCandidate> candidates = searchService.search(normalized, 20);

        if (candidates.isEmpty()) {
            return new MatchResult(MatchType.NO_MATCH, Optional.empty(), List.of(), 0.0,
                    "유사한 약물을 찾지 못함");
        }

        for (final MedicineCandidate candidate : candidates) {
            if (normalize(candidate.itemName()).equals(normalized)) {
                log.debug("Exact match: '{}' → '{}'", ocrText, candidate.itemName());
                return new MatchResult(MatchType.EXACT, Optional.of(candidate), List.of(), 1.0,
                        "정확 일치");
            }
        }

        final List<ScoredCandidate> scored = candidates.stream()
                .map(c -> new ScoredCandidate(c, levenshteinSimilarity(normalized, normalize(c.itemName()))))
                .sorted(Comparator.comparingDouble(ScoredCandidate::similarity).reversed())
                .toList();

        final ScoredCandidate top = scored.getFirst();

        if (top.similarity() >= fuzzyAutoThreshold) {
            final long highScoreCount = scored.stream()
                    .filter(s -> s.similarity() >= fuzzyAutoThreshold)
                    .count();

            final boolean dosageMatches = dosageHint
                    .map(hint -> top.candidate().mainIngr() != null
                            && top.candidate().mainIngr().toLowerCase().contains(hint.toLowerCase()))
                    .orElse(true);

            if (highScoreCount == 1 && dosageMatches) {
                final String reason = generateReason(ocrText, top.candidate(), top.similarity());
                log.info("Fuzzy auto match: '{}' → '{}' (sim={})",
                        ocrText, top.candidate().itemName(), top.similarity());
                return new MatchResult(MatchType.FUZZY_AUTO, Optional.of(top.candidate()),
                        List.of(), top.similarity(), reason);
            }
        }

        final List<MedicineCandidate> eligible = scored.stream()
                .filter(s -> s.similarity() >= MANUAL_REQUIRED_THRESHOLD)
                .limit(5)
                .map(ScoredCandidate::candidate)
                .toList();

        if (!eligible.isEmpty()) {
            return new MatchResult(MatchType.MANUAL_REQUIRED, Optional.empty(), eligible,
                    top.similarity(),
                    "동명이품 또는 유사 약물 " + eligible.size() + "개. 수동 선택 필요.");
        }

        return new MatchResult(MatchType.NO_MATCH, Optional.empty(), List.of(), top.similarity(),
                "유사한 약물을 찾지 못함");
    }

    private String generateReason(
            final String ocrText,
            final MedicineCandidate matched,
            final double similarity
    ) {
        final int editDist = levenshteinDistance(normalize(ocrText), normalize(matched.itemName()));
        String reason = "OCR '" + ocrText + "'와 "
                + editDist + "글자 차이. "
                + "식약처 등록 약물 '" + matched.itemName() + "'(으)로 자동 매칭.";
        return reason;
    }

    private record ScoredCandidate(
            MedicineCandidate candidate,
            double similarity
    ) {
    }
}
