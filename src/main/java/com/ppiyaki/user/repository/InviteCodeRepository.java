package com.ppiyaki.user.repository;

import com.ppiyaki.user.InviteCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    Optional<InviteCode> findByCodeHashAndUsedAtIsNull(final String codeHash);
}
