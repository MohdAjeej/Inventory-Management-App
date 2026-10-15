package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "units")
@Getter
@Setter
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Decimal places allowed for quantities in this unit (0 = whole numbers only). */
    private int decimals = 0;

    private boolean archived = false;

    @Version
    private long version;
}
