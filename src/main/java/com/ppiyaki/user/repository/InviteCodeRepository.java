package com.ppiyaki.user.repository;

import com.ppiyaki.user.InviteCode;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InviteCode> findByCodeHashAndUsedAtIsNull(final String codeHash);
}
