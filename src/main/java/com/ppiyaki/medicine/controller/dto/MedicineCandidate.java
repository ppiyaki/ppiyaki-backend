package com.ppiyaki.medicine.controller.dto;

import java.util.Map;

public record MedicineCandidate(
        String itemSeq,
        String itemName,
        String entpName,
        String mainIngr,
        String formName,
        String etcOtcName,
        String className,
        String ingrCode
) {

    public static MedicineCandidate fromMfdsItem(final Map<String, Object> item) {
        return new MedicineCandidate(
                getString(item, "ITEM_SEQ"),
                getString(item, "ITEM_NAME"),
                getString(item, "ENTP_NAME"),
                getString(item, "MATERIAL_NAME"),
                getString(item, "CHART"),
                getString(item, "ETC_OTC_CODE"),
                getString(item, "CLASS_NO"),
                null
        );
    }

    private static String getString(final Map<String, Object> item, final String key) {
        final Object value = item.get(key);
        return value != null ? value.toString() : null;
    }
}
