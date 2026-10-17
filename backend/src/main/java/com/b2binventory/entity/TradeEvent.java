package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One step in an invoice's history (sent, approved, rejected, return requested...), visible to both sides. */
@Entity
@Table(name = "trade_events")
@Getter
@Setter
public class TradeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long invoiceId;

    private Long returnId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private Long businessId;

    private Long membershipId;
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
