package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A customer return against an earlier sale. Resalable items go back into stock. */
@Entity
@Table(name = "sale_returns")
@Getter
@Setter
public class SaleReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(nullable = false)
    private String returnNumber;

    @Column(nullable = false)
    private Instant returnedAt;

    @Column(nullable = false)
    private String reason;

    /** Value of the returned goods (net + tax) credited to the customer. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal creditAmount;

    /** Money actually paid back to the customer. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    private String refundMethod;
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private Membership actor;

    @Column(nullable = false, updatable = false)
    private UUID clientRequestId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "saleReturn", cascade = CascadeType.ALL)
    private List<SaleReturnItem> items = new ArrayList<>();
}
