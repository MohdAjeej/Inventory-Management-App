package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Remembers which of the buyer's products a seller's product variant is received into. */
@Entity
@Table(name = "product_mappings")
@Getter
@Setter
public class ProductMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long buyerBusinessId;

    @Column(nullable = false, updatable = false)
    private Long sellerBusinessId;

    @Column(nullable = false, updatable = false)
    private Long sellerVariantId;

    @Column(nullable = false)
    private Long buyerVariantId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
