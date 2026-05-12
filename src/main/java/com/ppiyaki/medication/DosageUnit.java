package com.ppiyaki.medication;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 처방전·복약 일정의 단위 표기 정규화 enum.
 * spec docs/features/dosage-quantity-unit-split.md.
 *
 * 입력 변형(영문 약어/한국어/대소문자 차이)을 fromInput으로 정규화하여 저장한다.
 * 매칭 안 되는 입력은 OTHER로 흡수 (raw 텍스트 손실, 운영 모니터링으로 enum 추가 검토).
 */
public enum DosageUnit {

    /** 정/알 */
    TABLET("정"),
    /** 캡슐 */
    CAPSULE("캡슐"),
    /** 포 (산제) */
    PACKET("포"),
    /** 방울 */
    DROP("방울"),
    /** 밀리그램 */
    MG("mg"),
    /** 마이크로그램 */
    MCG("mcg"),
    /** 그램 */
    G("g"),
    /** 밀리리터 */
    ML("ml"),
    /** International Unit */
    IU("IU"),
    /** PRN — 필요 시 복용 (수량 정의 없음) */
    PRN("PRN"),
    /** 매칭 실패한 raw 입력 흡수용. 운영 데이터 보고 enum 추가 검토 */
    OTHER("");

    private final String displayValue;

    DosageUnit(final String displayValue) {
        this.displayValue = displayValue;
    }

    /**
     * 프론트 응답용 대표 표기. 프론트 변경 없이 그대로 표시 가능.
     * OTHER는 빈 문자열 (운영 데이터 보고 정책 정정).
     */
    public String getDisplayValue() {
        return displayValue;
    }

    private static final Map<String, DosageUnit> ALIASES = Map.ofEntries(
            // TABLET
            Map.entry("정", TABLET),
            Map.entry("알", TABLET),
            Map.entry("tab", TABLET),
            Map.entry("tablet", TABLET),
            // CAPSULE
            Map.entry("캡슐", CAPSULE),
            Map.entry("cap", CAPSULE),
            Map.entry("capsule", CAPSULE),
            // PACKET
            Map.entry("포", PACKET),
            Map.entry("pack", PACKET),
            Map.entry("packet", PACKET),
            // DROP
            Map.entry("방울", DROP),
            Map.entry("drop", DROP),
            // MG
            Map.entry("mg", MG),
            Map.entry("밀리그람", MG),
            Map.entry("밀리그램", MG),
            // MCG
            Map.entry("mcg", MCG),
            Map.entry("ug", MCG),
            Map.entry("μg", MCG),
            Map.entry("마이크로그람", MCG),
            Map.entry("마이크로그램", MCG),
            // G
            Map.entry("g", G),
            Map.entry("그람", G),
            Map.entry("그램", G),
            // ML
            Map.entry("ml", ML),
            Map.entry("밀리리터", ML),
            // IU
            Map.entry("iu", IU),
            Map.entry("단위", IU),
            // PRN
            Map.entry("prn", PRN),
            Map.entry("필요시", PRN),
            Map.entry("필요 시", PRN)
    );

    /**
     * 자유 입력 String을 DosageUnit으로 정규화. null/blank → Optional.empty.
     * 정확 매칭(소문자 비교) → 그 enum, 매칭 실패 → OTHER (raw 입력은 손실).
     */
    public static Optional<DosageUnit> fromInput(final String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        final String key = input.trim().toLowerCase(Locale.ROOT);
        // 우선 enum.name() 자체 매칭 시도 (이미 정규화된 값 재처리 안전)
        try {
            return Optional.of(DosageUnit.valueOf(input.trim().toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException ignored) {
            // 별칭 매칭 시도
        }
        final DosageUnit matched = ALIASES.get(key);
        return Optional.of(matched != null ? matched : OTHER);
    }
}
