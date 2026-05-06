package com.ppiyaki.user.repository;

import com.ppiyaki.user.InviteCode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    List<InviteCode> findBySeniorIdAndUsedAtIsNullOrderByCreatedAtDesc(final Long seniorId);
}
