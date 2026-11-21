package com.pratosh.moneytracker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "recurring_rules", indexes = @Index(name = "idx_rec_owner_next", columnList = "owner_id,next_run"))
@Check(constraints = "amount > 0")
@Getter
@Setter
public class RecurringRule extends BaseEntity {
    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(length = 200)
    private String description;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod = "cash";

    /** weekly, monthly, yearly */
    @Column(nullable = false, length = 10)
    private String frequency = "monthly";

    @Column(name = "next_run", nullable = false)
    private LocalDate nextRun;

    @Column(nullable = false)
    private boolean active = true;
}
