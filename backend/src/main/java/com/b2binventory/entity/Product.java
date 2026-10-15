package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "products")
@Getter
@Setter
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_unit_id")
    private Unit baseUnit;

    @Column(nullable = false)
    private String name;

    private String brand;
    private String model;
    private String description;

    /** Product-specific low-stock threshold; null falls back to the business default. */
    private Integer minStock;

    /** Default selling price per base unit; prefills sales. */
    @Column(precision = 18, scale = 2)
    private BigDecimal salePrice;

    /** Default purchase price per base unit; prefills purchases. Updated to the latest received cost. */
    @Column(precision = 18, scale = 2)
    private BigDecimal purchasePrice;

    /** Weighted-average cost per base unit of the stock on hand; used for the cost of goods sold. */
    @Column(precision = 18, scale = 4)
    private BigDecimal averageCost;

    /** Default tax rate (percent) applied to sales and purchases of this product. */
    @Column(precision = 5, scale = 2)
    private BigDecimal taxRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** Cost used for a sale: the weighted average, else the default purchase price, else unknown. */
    public BigDecimal costBasis() {
        return averageCost != null ? averageCost : purchasePrice != null ? purchasePrice.setScale(4) : null;
    }

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> attributes = new LinkedHashMap<>();

    private boolean trackBatches = false;
    private boolean trackSerials = false;
    private boolean archived = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
