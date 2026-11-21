package com.pratosh.moneytracker.repo;

import com.pratosh.moneytracker.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<Goal> findByIdAndOwnerId(Long id, Long ownerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Goal g where g.ownerId = :owner and g.demo = true")
    int deleteDemo(@Param("owner") Long owner);
}
