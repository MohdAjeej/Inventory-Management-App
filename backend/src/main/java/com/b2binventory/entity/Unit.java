package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A unit of measure: a standard unit shared by every business (no business) or a business's own custom unit. */
@Entity
@Table(name = "units")
@Getter
@Setter
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for standard units. */
    @Column(name = "business_id", updatable = false)
    private Long businessId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Decimal places allowed for quantities in this unit (0 = whole numbers only). */
    private int decimals = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitCategory category = UnitCategory.OTHER;

    /** How many of the category's base unit one of this unit equals; null when the unit has no fixed size. */
    @Column(name = "base_factor", precision = 30, scale = 15)
    private BigDecimal baseFactor;

    private boolean archived = false;

    @Version
    private long version;

    public boolean isStandard() {
        return businessId == null;
    }
}
