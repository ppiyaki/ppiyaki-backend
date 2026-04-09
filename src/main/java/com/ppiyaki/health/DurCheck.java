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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "dur_checks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DurCheck extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "medicine_id", nullable = false)
    private Long medicineId;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "warning_level")
    private DurWarningLevel warningLevel;

    @Column(columnDefinition = "TEXT", name = "warning_text")
    private String warningText;

    @Column(columnDefinition = "TEXT", name = "raw_response")
    private String rawResponse;

    public DurCheck(
            final Long medicineId,
            final LocalDateTime checkedAt,
            final DurWarningLevel warningLevel,
            final String warningText,
            final String rawResponse) {
        this.medicineId = medicineId;
        this.checkedAt = checkedAt;
        this.warningLevel = warningLevel;
        this.warningText = warningText;
        this.rawResponse = rawResponse;
    }
}
