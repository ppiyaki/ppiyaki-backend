package com.ppiyaki.medication.repository;

import com.ppiyaki.medication.MedicationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, Long> {

    int countByMedicineId(final Long medicineId);

    void deleteByMedicineId(final Long medicineId);
}
