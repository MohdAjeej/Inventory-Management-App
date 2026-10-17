package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** One invoiced product: a snapshot of the seller's product, variant, unit and agreed price. */
@Entity
@Table(name = "trade_invoice_lines")
@Getter
@Setter
public class TradeInvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private TradeInvoice invoice;

    @Column(nullable = false)
    private int lineNo;

    @Column(nullable = false)
    private Long sellerVariantId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private String variantName;

    private boolean defaultVariant = true;
    private String sku;
    private String barcode;
    private String brand;
    private String model;
    private String categoryName;
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> variantAttributes = new LinkedHashMap<>();

    @Column(nullable = false)
    private String unitCode;

    @Column(nullable = false)
    private String unitName;

    private int unitDecimals;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal grossAmount;

    @Column(nullable = false)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal netAmount;

    @Column(nullable = false)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal lineTotal;

    private Long buyerVariantId;

    @Column(nullable = false)
    private BigDecimal returnedQuantity = BigDecimal.ZERO;
}
