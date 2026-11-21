package com.pratosh.moneytracker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categories", indexes = @Index(name = "idx_cat_owner", columnList = "owner_id,kind"))
@Getter
@Setter
public class Category extends BaseEntity {
    @Column(nullable = false, length = 40)
    private String name;

    /** "income" or "expense" */
    @Column(nullable = false, length = 10)
    private String kind;

    @Column(nullable = false, length = 7)
    private String color = "#64748b";
}
