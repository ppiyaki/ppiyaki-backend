package com.ppiyaki.medication;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * dosage 자유 텍스트("1정", "2알", "1.5캡슐")에서 정수 알약 개수 추출.
 * spec medication-log-phase2 §5-3.
 */
public final class DosageParser {

    private static final Pattern PILL_COUNT_PATTERN = Pattern.compile("(\\d+)");

    private DosageParser() {
    }

    /**
     * dosage 문자열에서 첫 정수 매치를 반환한다.
     * 매치 없거나 null/blank 입력은 Optional.empty.
     */
    public static Optional<Integer> parsePillCount(final String dosage) {
        if (dosage == null || dosage.isBlank()) {
            return Optional.empty();
        }
        final Matcher matcher = PILL_COUNT_PATTERN.matcher(dosage);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }
}
