package com.ppiyaki.health.repository;

import com.ppiyaki.health.DurCheck;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DurCheckRepository extends JpaRepository<DurCheck, Long> {

    Optional<DurCheck> findFirstByMedicineIdAndComboHashAndCheckedAtAfterAndWarningLevelIsNotNullOrderByCheckedAtDesc(
            final Long medicineId,
            final String comboHash,
            final LocalDateTime after
    );

    List<DurCheck> findByMedicineIdOrderByCheckedAtDesc(final Long medicineId, final Pageable pageable);

    Optional<DurCheck> findFirstByMedicineIdAndWarningLevelIsNotNullOrderByCheckedAtDesc(final Long medicineId);
}
