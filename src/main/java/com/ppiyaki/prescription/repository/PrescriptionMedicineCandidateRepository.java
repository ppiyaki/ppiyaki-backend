package com.ppiyaki.prescription.repository;

import com.ppiyaki.prescription.PrescriptionMedicineCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionMedicineCandidateRepository
        extends JpaRepository<PrescriptionMedicineCandidate, Long> {

    List<PrescriptionMedicineCandidate> findByPrescriptionId(final Long prescriptionId);
}
