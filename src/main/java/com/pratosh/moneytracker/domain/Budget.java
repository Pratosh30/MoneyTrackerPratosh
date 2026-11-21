package com.pratosh.moneytracker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "budgets",
        uniqueConstraints = @UniqueConstraint(name = "uk_budget_period",
                columnNames = {"owner_id", "category_id", "budget_month", "period"}),
        indexes = @Index(name = "idx_budget_owner_month", columnList = "owner_id,budget_month"))
@Check(constraints = "amount > 0")
@Getter
@Setter
public class Budget extends BaseEntity {
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    /** First day of the month (period=month) or of the year (period=year). */
    @Column(name = "budget_month", nullable = false)
    private LocalDate monthStart;

    /** "month" or "year" */
    @Column(nullable = false, length = 10)
    private String period = "month";

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
