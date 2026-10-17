package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** An invoice from one business (seller) to a connected business (buyer). */
@Entity
@Table(name = "trade_invoices")
@Getter
@Setter
public class TradeInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String invoiceNumber;

    @Column(nullable = false, updatable = false)
    private Long sellerBusinessId;

    @Column(nullable = false)
    private Long buyerBusinessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeInvoiceStatus status = TradeInvoiceStatus.DRAFT;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    private String notes;
    private String deliveryNotes;

    @Column(nullable = false)
    private Long sellerWarehouseId;

    private Long buyerWarehouseId;

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant sentAt;
    private Long respondedBy;
    private Instant respondedAt;
    private String rejectionReason;
    private String cancelReason;
    private Long sellerSaleId;
    private Long buyerPurchaseId;

    @Column(updatable = false)
    private UUID clientRequestId;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<TradeInvoiceLine> lines = new ArrayList<>();

    @Version
    private long version;
}
