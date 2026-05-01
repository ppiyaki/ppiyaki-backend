package com.ppiyaki.medication.repository;

import com.ppiyaki.medication.MedicationLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationLogRepository extends JpaRepository<MedicationLog, Long> {

    Optional<MedicationLog> findByScheduleIdAndTargetDate(final Long scheduleId, final LocalDate targetDate);

    List<MedicationLog> findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(
            final Long seniorId, final LocalDate from, final LocalDate to);
}
