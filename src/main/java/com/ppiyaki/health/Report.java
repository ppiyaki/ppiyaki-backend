package com.ppiyaki.health;

import com.ppiyaki.common.entity.CreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "reports", uniqueConstraints = @UniqueConstraint(
                name = "uk_reports_senior_period", columnNames = {"senior_id", "period_type", "period_start"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_id", nullable = false)
    private Long seniorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false)
    private ReportPeriodType periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_scheduled", nullable = false)
    private Integer totalScheduled;

    @Column(name = "total_taken", nullable = false)
    private Integer totalTaken;

    @Column(name = "total_missed", nullable = false)
    private Integer totalMissed;

    @Column(name = "adherence_rate", precision = 5, scale = 2)
    private BigDecimal adherenceRate;

    public Report(
            final Long seniorId,
            final ReportPeriodType periodType,
            final LocalDate periodStart,
            final LocalDate periodEnd,
            final Integer totalScheduled,
            final Integer totalTaken,
            final Integer totalMissed,
            final BigDecimal adherenceRate) {
        this.seniorId = seniorId;
        this.periodType = periodType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalScheduled = totalScheduled;
        this.totalTaken = totalTaken;
        this.totalMissed = totalMissed;
        this.adherenceRate = adherenceRate;
    }
}
