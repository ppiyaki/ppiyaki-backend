package com.ppiyaki.user.repository;

import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.domain.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByLoginId(final String loginId);

    Optional<User> findByLoginId(final String loginId);

    @Query("""
            SELECT u FROM User u
            WHERE u.role = :role
              AND u.breakfastTime IS NOT NULL
              AND u.lunchTime IS NOT NULL
              AND u.dinnerTime IS NOT NULL
            """)
    List<User> findAllByRoleWithMealTimesSet(@Param("role") final UserRole role);
}
