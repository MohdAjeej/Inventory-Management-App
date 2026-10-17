package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Goods the buyer sends back against a posted trade invoice. */
@Entity
@Table(name = "trade_returns")
@Getter
@Setter
public class TradeReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private TradeInvoice invoice;

    @Column(nullable = false)
    private String returnNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeReturnStatus status = TradeReturnStatus.REQUESTED;

    @Column(nullable = false)
    private String reason;

    private String notes;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false)
    private Long requestedBy;

    @Column(nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    private Long respondedBy;
    private Instant respondedAt;
    private String responseNote;
    private Long sellerSaleReturnId;

    @Column(updatable = false)
    private UUID clientRequestId;

    @OneToMany(mappedBy = "tradeReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<TradeReturnLine> lines = new ArrayList<>();

    @Version
    private long version;
}
