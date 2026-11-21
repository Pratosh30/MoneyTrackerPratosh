package com.pratosh.moneytracker.repo;

import com.pratosh.moneytracker.domain.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {
}
