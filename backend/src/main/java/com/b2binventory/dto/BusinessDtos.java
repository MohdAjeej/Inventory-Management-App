package com.b2binventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class BusinessDtos {

    private BusinessDtos() {
    }

    public record BusinessResponse(
            Long id, String name, String businessType, String logoUrl, String email, String phone,
            String address, String city, String state, String country, String postalCode, Instant createdAt,
            /* public code other businesses can search for, and the tax registration number */
            String businessCode, String taxId) {
    }

    public record UpdateBusinessRequest(
            @NotBlank(message = "Business name is required") @Size(max = 200) String name,
            @Size(max = 120) String businessType,
            @Email(message = "Email is invalid") @Size(max = 200) String email,
            @Size(max = 50) String phone,
            @Size(max = 500) String address,
            @Size(max = 120) String city,
            @Size(max = 120) String state,
            @Size(max = 120) String country,
            @Size(max = 30) String postalCode,
            /* tax registration such as GSTIN; null leaves it unchanged, blank clears it */
            @Size(max = 50) String taxId) {
    }

    public record SettingsResponse(
            boolean allowNegativeStock, int defaultLowStockThreshold, boolean trackBatches, boolean trackExpiry,
            boolean trackSerials, boolean trackDamagedReserved, boolean notifyLowStock, String timezone,
            String currencyCode) {
    }

    public record UpdateSettingsRequest(
            boolean allowNegativeStock,
            @Min(0) @Max(1_000_000) int defaultLowStockThreshold,
            boolean trackBatches,
            boolean trackExpiry,
            boolean trackSerials,
            boolean trackDamagedReserved,
            boolean notifyLowStock,
            /* null keeps the current value */
            @Size(max = 64) String timezone,
            @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter code") String currencyCode) {
    }
}
