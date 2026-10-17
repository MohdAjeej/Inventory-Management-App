package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A link between two businesses that lets them send each other invoices. */
@Entity
@Table(name = "business_connections")
@Getter
@Setter
public class BusinessConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long requesterBusinessId;

    @Column(nullable = false, updatable = false)
    private Long addresseeBusinessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus status = ConnectionStatus.PENDING;

    private String message;

    @Column(nullable = false, updatable = false)
    private Long requestedBy;

    private Long respondedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant respondedAt;

    @Version
    private long version;

    public boolean involves(Long businessId) {
        return requesterBusinessId.equals(businessId) || addresseeBusinessId.equals(businessId);
    }

    public Long otherThan(Long businessId) {
        return requesterBusinessId.equals(businessId) ? addresseeBusinessId : requesterBusinessId;
    }
}
