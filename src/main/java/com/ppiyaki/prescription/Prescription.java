package com.ppiyaki.prescription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.ppiyaki.common.entity.CreatedTimeEntity;

@Entity
@Getter
@Table(name = "prescriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_id")
    private Long seniorId;

    @Column(name = "caregiver_id")
    private Long caregiverId;

    @Column(name = "ocr_image_url")
    private String ocrImageUrl;

    @Column(columnDefinition = "TEXT", name = "extracted_text")
    private String extractedText;

    @Column(name = "status")
    private String status;

    public Prescription(
            final Long seniorId,
            final Long caregiverId,
            final String ocrImageUrl,
            final String extractedText,
            final String status
    ) {
        this.seniorId = seniorId;
        this.caregiverId = caregiverId;
        this.ocrImageUrl = ocrImageUrl;
        this.extractedText = extractedText;
        this.status = status;
    }
}
