package com.pratosh.moneytracker.repo;

import com.pratosh.moneytracker.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<Account> findByIdAndOwnerId(Long id, Long ownerId);
}
