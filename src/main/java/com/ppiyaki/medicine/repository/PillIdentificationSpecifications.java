package com.ppiyaki.medicine.repository;

import com.ppiyaki.medicine.PillIdentification;
import org.springframework.data.jpa.domain.Specification;

/**
 * 외형(각인·색·모양·분할선) 동적 검색 Specification.
 * spec docs/features/pill-identification.md §5-2 — null 파라미터는 제외.
 */
public final class PillIdentificationSpecifications {

    private PillIdentificationSpecifications() {
    }

    public static Specification<PillIdentification> byAppearance(
            final String printFront,
            final String printBack,
            final String drugShape,
            final String colorClass1,
            final String lineFront
    ) {
        return Specification.allOf(
                fieldEquals("printFront", printFront),
                fieldEquals("printBack", printBack),
                fieldEquals("drugShape", drugShape),
                fieldEquals("colorClass1", colorClass1),
                fieldEquals("lineFront", lineFront)
        );
    }

    private static Specification<PillIdentification> fieldEquals(final String field, final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }
}
