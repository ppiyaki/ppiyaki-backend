package com.ppiyaki.prescription.repository;

import com.ppiyaki.prescription.Prescription;
import com.ppiyaki.prescription.PrescriptionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByOwnerIdAndStatusOrderByCreatedAtDesc(
            final Long ownerId,
            final PrescriptionStatus status
    );

    List<Prescription> findByOwnerIdOrderByCreatedAtDesc(final Long ownerId);
}
