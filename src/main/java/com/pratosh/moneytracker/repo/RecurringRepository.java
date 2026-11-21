package com.pratosh.moneytracker.repo;

import com.pratosh.moneytracker.domain.RecurringRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringRepository extends JpaRepository<RecurringRule, Long> {
    List<RecurringRule> findByOwnerIdOrderByNextRunAsc(Long ownerId);

    Optional<RecurringRule> findByIdAndOwnerId(Long id, Long ownerId);

    List<RecurringRule> findByOwnerIdAndActiveTrueAndNextRunLessThanEqual(Long ownerId, LocalDate date);

    List<RecurringRule> findByActiveTrueAndNextRunLessThanEqual(LocalDate date);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RecurringRule r where r.ownerId = :owner and r.demo = true")
    int deleteDemo(@Param("owner") Long owner);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RecurringRule r set r.categoryId = null where r.ownerId = :owner and r.categoryId = :cat")
    int clearCategory(@Param("owner") Long owner, @Param("cat") Long cat);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RecurringRule r set r.accountId = null where r.ownerId = :owner and r.accountId = :acc")
    int clearAccount(@Param("owner") Long owner, @Param("acc") Long acc);
}
