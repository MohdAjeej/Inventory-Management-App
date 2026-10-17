package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "businesses")
@Getter
@Setter
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "business_type")
    private String businessType;

    private String logoUrl;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String country;
    private String postalCode;

    /** Short public code other businesses can search for, e.g. B7K3F9QA. */
    @Column(name = "business_code", updatable = false)
    private String businessCode;

    /** Tax registration number (e.g. GSTIN), shown on trade invoices. */
    private String taxId;

    /** Whether other businesses can find this one in Business network search. */
    private boolean discoverable = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        if (businessCode == null) {
            businessCode = "B" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
