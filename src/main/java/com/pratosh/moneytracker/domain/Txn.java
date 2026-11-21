package com.pratosh.moneytracker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_txn_owner_date", columnList = "owner_id,occurred_at"),
        @Index(name = "idx_txn_owner_cat", columnList = "owner_id,category_id"),
        @Index(name = "idx_txn_owner_type", columnList = "owner_id,type")
})
@Check(constraints = "amount > 0")
@Getter
@Setter
public class Txn extends BaseEntity {
    /** "income" or "expense" */
    @Column(nullable = false, length = 10)
    private String type;

    /** Exact decimal storage - never floating point. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Currency the amount was recorded in (kept even if the user later changes currency). */
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(length = 200)
    private String description;

    @Column(length = 1000)
    private String notes;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod = "cash";

    /** Local wall-clock date and time chosen by the user. */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
