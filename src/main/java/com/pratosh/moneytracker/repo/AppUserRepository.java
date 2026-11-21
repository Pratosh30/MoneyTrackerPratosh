package com.pratosh.moneytracker.repo;

import com.pratosh.moneytracker.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUserKey(String userKey);

    boolean existsByUserKey(String userKey);
}
