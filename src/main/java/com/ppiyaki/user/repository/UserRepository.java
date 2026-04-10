package com.ppiyaki.user.repository;

import com.ppiyaki.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByLoginId(final String loginId);

    Optional<User> findByLoginId(final String loginId);
}
