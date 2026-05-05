package com.ppiyaki.medication.repository;

import com.ppiyaki.medication.MedicationSchedule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, Long> {

    List<MedicationSchedule> findByMedicineId(final Long medicineId);

    int deleteByMedicineId(final Long medicineId);

    /**
     * 같은 시니어가 같은 시각에 복용 예정인 active schedule 전체.
     * Phase 2 약 개수 검증의 기대 카운트 산출용 (medication-log-phase2 §5-4).
     */
    @Query("""
            SELECT s FROM MedicationSchedule s
            WHERE s.medicineId IN (SELECT m.id FROM Medicine m WHERE m.ownerId = :seniorId)
              AND s.scheduledTime = :time
              AND (s.startDate IS NULL OR s.startDate <= :date)
              AND (s.endDate IS NULL OR s.endDate >= :date)
            """)
    List<MedicationSchedule> findActiveByOwnerAndScheduledTime(
            @Param("seniorId") final Long seniorId,
            @Param("date") final LocalDate date,
            @Param("time") final LocalTime time);
}
