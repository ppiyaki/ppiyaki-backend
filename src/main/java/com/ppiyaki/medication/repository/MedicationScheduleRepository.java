package com.ppiyaki.medication.repository;

import com.ppiyaki.medication.MedicationSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, Long> {

    List<MedicationSchedule> findByMedicineId(final Long medicineId);

    int deleteByMedicineId(final Long medicineId);
}
