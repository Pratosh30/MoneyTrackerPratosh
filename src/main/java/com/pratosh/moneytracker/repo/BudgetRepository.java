package com.pratosh.moneytracker.repo;

import com.pratosh.moneytracker.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    Optional<Budget> findByIdAndOwnerId(Long id, Long ownerId);

    List<Budget> findByOwnerId(Long ownerId);

    List<Budget> findByOwnerIdAndPeriodAndMonthStart(Long ownerId, String period, LocalDate monthStart);

    boolean existsByOwnerIdAndCategoryIdAndPeriodAndMonthStart(Long ownerId, Long categoryId, String period, LocalDate monthStart);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Budget b where b.ownerId = :owner and b.demo = true")
    int deleteDemo(@Param("owner") Long owner);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Budget b where b.ownerId = :owner and b.categoryId = :cat")
    int deleteForCategory(@Param("owner") Long owner, @Param("cat") Long cat);
}
