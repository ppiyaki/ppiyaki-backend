package com.ppiyaki.user.repository;

import com.ppiyaki.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
