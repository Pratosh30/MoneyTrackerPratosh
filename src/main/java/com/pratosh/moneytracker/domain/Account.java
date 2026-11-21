package com.pratosh.moneytracker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts", indexes = @Index(name = "idx_acc_owner", columnList = "owner_id"))
@Getter
@Setter
public class Account extends BaseEntity {
    @Column(nullable = false, length = 40)
    private String name;

    /** cash, bank, card, upi, wallet, other */
    @Column(nullable = false, length = 10)
    private String type = "cash";

    @Column(name = "opening_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean archived;
}
