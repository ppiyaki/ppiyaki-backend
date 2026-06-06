package com.ppiyaki.medicine.controller.dto;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record MedicineCandidate(
        String itemSeq,
        String itemName,
        String entpName,
        String mainIngr,
        String formName,
        String etcOtcCode,
        String className
) {

    public static MedicineCandidate fromMfdsItem(final Map<String, Object> item) {
        return new MedicineCandidate(
                getString(item, "ITEM_SEQ"),
                getString(item, "ITEM_NAME"),
                getString(item, "ENTP_NAME"),
                formatMainIngredient(getString(item, "MATERIAL_NAME")),
                getString(item, "CHART"),
                getString(item, "ETC_OTC_CODE"),
                getString(item, "CLASS_NO")
        );
    }

    /**
     * 식약처 MATERIAL_NAME 원본을 사람이 읽을 수 있는 주성분 문구로 변환한다.
     * 원본 형식: 성분마다 {@code /} 로 구분, 각 성분은 {@code 성분명,총량,분량,단위,규격,비고} 를
     * 쉼표로 나열한다. 분량 등이 비면 {@code ,,,} 가 그대로 노출되고 규격(USP·KP)·총량 코드처럼
     * 사용자에게 의미 없는 값이 섞이므로, 성분명과 분량·단위만 추려 {@code "성분명 분량단위, ..."} 로 만든다.
     */
    private static String formatMainIngredient(final String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        final Set<String> ingredients = new LinkedHashSet<>();
        for (final String segment : materialName.split("/")) {
            final String[] fields = segment.split(",", -1);
            final String ingredientName = fields[0].strip();
            if (ingredientName.isEmpty()) {
                continue;
            }
            final String amount = fields.length > 2 ? normalizeAmount(fields[2].strip()) : "";
            final String unit = fields.length > 3 ? fields[3].strip() : "";
            ingredients.add(amount.isEmpty() ? ingredientName : ingredientName + " " + amount + unit);
        }
        return ingredients.isEmpty() ? null : String.join(", ", ingredients);
    }

    private static String normalizeAmount(final String amount) {
        if (!amount.contains(".")) {
            return amount;
        }
        return amount.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String getString(final Map<String, Object> item, final String key) {
        final Object value = item.get(key);
        return value != null ? value.toString() : null;
    }
}
