package com.pratosh.moneytracker.repo;

import com.pratosh.moneytracker.domain.Txn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TxnRepository extends JpaRepository<Txn, Long>, JpaSpecificationExecutor<Txn> {
    Optional<Txn> findByIdAndOwnerId(Long id, Long ownerId);

    List<Txn> findByOwnerIdOrderByOccurredAtDescIdDesc(Long ownerId);

    List<Txn> findByOwnerIdAndCurrency(Long ownerId, String currency);

    boolean existsByOwnerIdAndCurrencyNot(Long ownerId, String currency);

    @Query("select min(t.occurredAt) from Txn t where t.ownerId = :owner")
    LocalDateTime earliest(@Param("owner") Long owner);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Txn t where t.ownerId = :owner and t.demo = true")
    int deleteDemo(@Param("owner") Long owner);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Txn t set t.categoryId = null where t.ownerId = :owner and t.categoryId = :cat")
    int clearCategory(@Param("owner") Long owner, @Param("cat") Long cat);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Txn t set t.accountId = null where t.ownerId = :owner and t.accountId = :acc")
    int clearAccount(@Param("owner") Long owner, @Param("acc") Long acc);
}
