package com.ppiyaki.medicine.repository;

import com.ppiyaki.medicine.PillIdentification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PillIdentificationRepository
        extends JpaRepository<PillIdentification, String>, JpaSpecificationExecutor<PillIdentification> {
}
