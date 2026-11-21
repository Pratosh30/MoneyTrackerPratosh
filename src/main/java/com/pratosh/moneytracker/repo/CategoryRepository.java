package com.pratosh.moneytracker.repo;

import com.pratosh.moneytracker.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByOwnerIdOrderByKindAscNameAsc(Long ownerId);

    Optional<Category> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndKindAndNameIgnoreCase(Long ownerId, String kind, String name);
}
