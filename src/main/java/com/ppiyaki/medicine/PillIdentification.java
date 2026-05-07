package com.ppiyaki.medicine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 식약처 의약품 낱알식별 정보 마스터.
 * spec docs/features/pill-identification.md.
 *
 * <p>식약처 OpenAPI {@code MdcinGrnIdntfcInfoService03/getMdcinGrnIdntfcInfoList03}를
 * 주 1회 batch로 동기화한다. 외형(각인·색·모양·분할선)으로 검색해 사진에서
 * 약명을 추정 못하는 vision 흐름의 식별 백업으로 사용된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "pill_identifications", indexes = {
                @Index(name = "idx_pill_print_front", columnList = "print_front"),
                @Index(name = "idx_pill_shape_color", columnList = "drug_shape, color_class1"),
                @Index(name = "idx_pill_color_shape_line", columnList = "color_class1, drug_shape, line_front"),
                @Index(name = "idx_pill_item_name", columnList = "item_name")
        }
)
public class PillIdentification {

    @Id
    @Column(name = "item_seq", length = 20, nullable = false)
    private String itemSeq;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "entp_name")
    private String entpName;

    @Column(name = "print_front", length = 64)
    private String printFront;

    @Column(name = "print_back", length = 64)
    private String printBack;

    @Column(name = "drug_shape", length = 32)
    private String drugShape;

    @Column(name = "color_class1", length = 32)
    private String colorClass1;

    @Column(name = "color_class2", length = 32)
    private String colorClass2;

    @Column(name = "line_front", length = 32)
    private String lineFront;

    @Column(name = "line_back", length = 32)
    private String lineBack;

    @Column(name = "leng_long", length = 16)
    private String lengLong;

    @Column(name = "leng_short", length = 16)
    private String lengShort;

    @Column(name = "thick", length = 16)
    private String thick;

    @Column(name = "chart", columnDefinition = "TEXT")
    private String chart;

    @Column(name = "item_image", length = 512)
    private String itemImage;

    @Column(name = "class_no", length = 16)
    private String classNo;

    @Column(name = "class_name", length = 128)
    private String className;

    @Column(name = "etc_otc_name", length = 32)
    private String etcOtcName;

    @Column(name = "mark_code_front", length = 64)
    private String markCodeFront;

    @Column(name = "mark_code_back", length = 64)
    private String markCodeBack;

    @Column(name = "edi_code", length = 32)
    private String ediCode;

    @Column(name = "bizrno", length = 32)
    private String bizrno;

    @Column(name = "change_date", length = 20)
    private String changeDate;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    public PillIdentification(
            final String itemSeq,
            final String itemName,
            final String entpName,
            final String printFront,
            final String printBack,
            final String drugShape,
            final String colorClass1,
            final String colorClass2,
            final String lineFront,
            final String lineBack,
            final String lengLong,
            final String lengShort,
            final String thick,
            final String chart,
            final String itemImage,
            final String classNo,
            final String className,
            final String etcOtcName,
            final String markCodeFront,
            final String markCodeBack,
            final String ediCode,
            final String bizrno,
            final String changeDate,
            final LocalDateTime syncedAt
    ) {
        this.itemSeq = Objects.requireNonNull(itemSeq, "itemSeq must not be null");
        this.itemName = Objects.requireNonNull(itemName, "itemName must not be null");
        this.entpName = entpName;
        this.printFront = printFront;
        this.printBack = printBack;
        this.drugShape = drugShape;
        this.colorClass1 = colorClass1;
        this.colorClass2 = colorClass2;
        this.lineFront = lineFront;
        this.lineBack = lineBack;
        this.lengLong = lengLong;
        this.lengShort = lengShort;
        this.thick = thick;
        this.chart = chart;
        this.itemImage = itemImage;
        this.classNo = classNo;
        this.className = className;
        this.etcOtcName = etcOtcName;
        this.markCodeFront = markCodeFront;
        this.markCodeBack = markCodeBack;
        this.ediCode = ediCode;
        this.bizrno = bizrno;
        this.changeDate = changeDate;
        this.syncedAt = Objects.requireNonNull(syncedAt, "syncedAt must not be null");
    }

    /**
     * 동기화 시 외형 등 메타 갱신. PK는 유지(item_seq).
     */
    public void updateFromSync(final PillIdentification fresh) {
        this.itemName = fresh.itemName;
        this.entpName = fresh.entpName;
        this.printFront = fresh.printFront;
        this.printBack = fresh.printBack;
        this.drugShape = fresh.drugShape;
        this.colorClass1 = fresh.colorClass1;
        this.colorClass2 = fresh.colorClass2;
        this.lineFront = fresh.lineFront;
        this.lineBack = fresh.lineBack;
        this.lengLong = fresh.lengLong;
        this.lengShort = fresh.lengShort;
        this.thick = fresh.thick;
        this.chart = fresh.chart;
        this.itemImage = fresh.itemImage;
        this.classNo = fresh.classNo;
        this.className = fresh.className;
        this.etcOtcName = fresh.etcOtcName;
        this.markCodeFront = fresh.markCodeFront;
        this.markCodeBack = fresh.markCodeBack;
        this.ediCode = fresh.ediCode;
        this.bizrno = fresh.bizrno;
        this.changeDate = fresh.changeDate;
        this.syncedAt = fresh.syncedAt;
    }
}
