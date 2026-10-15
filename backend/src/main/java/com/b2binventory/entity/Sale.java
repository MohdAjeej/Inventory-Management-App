package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A recorded sale (one invoice) with one or more line items. Recording it issues stock for every line
 * in the same transaction. Cancelling it restores the stock that has not already been returned.
 */
@Entity
@Table(name = "sales")
@Getter
@Setter
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(nullable = false)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private String customerName;
    private String customerPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaleStatus status = SaleStatus.COMPLETED;

    @Column(nullable = false)
    private Instant soldAt;

    /** Sum of quantity x unit price over all lines, before discounts and tax. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    /** Amount charged: subtotal - discounts + tax. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    /** Cost of goods at the time of sale; null when any line had no known cost. */
    @Column(precision = 18, scale = 2)
    private BigDecimal costTotal;

    /** Value of returned goods credited back to the customer (including tax). */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal returnedTotal = BigDecimal.ZERO;

    /** Net money received: payments minus refunds. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private Membership actor;

    private String cancelReason;
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private Membership cancelledBy;

    @Column(nullable = false, updatable = false)
    private UUID clientRequestId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    @OrderBy("lineNo ASC")
    private List<SaleItem> items = new ArrayList<>();

    @Version
    private long version;

    /** Amount still owed by the customer (never negative). */
    public BigDecimal outstanding() {
        BigDecimal due = total.subtract(returnedTotal).subtract(amountPaid);
        return due.signum() > 0 ? due : BigDecimal.ZERO;
    }
}
