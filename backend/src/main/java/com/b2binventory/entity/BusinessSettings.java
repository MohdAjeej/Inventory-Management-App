package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "business_settings")
@Getter
@Setter
public class BusinessSettings {

    @Id
    @Column(name = "business_id")
    private Long businessId;

    private boolean allowNegativeStock = false;
    private int defaultLowStockThreshold = 5;
    private boolean trackBatches = false;
    private boolean trackExpiry = false;
    private boolean trackSerials = false;
    private boolean trackDamagedReserved = false;
    private boolean notifyLowStock = true;

    /** IANA zone used for date filters and daily analytics buckets. */
    @Column(nullable = false)
    private String timezone = "UTC";

    /** ISO 4217 code; display only. */
    @Column(nullable = false)
    private String currencyCode = "INR";

    @Version
    private long version;
}
