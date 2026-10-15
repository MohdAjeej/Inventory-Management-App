package com.b2binventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Customers and suppliers share the same simple shape; taxId only applies to suppliers. */
public final class PartyDtos {

    private PartyDtos() {
    }

    public record PartyRequest(
            @NotBlank(message = "Name is required") @Size(max = 200) String name,
            @Size(max = 50) String phone,
            @Email(message = "Email is invalid") @Size(max = 200) String email,
            @Size(max = 500) String address,
            @Size(max = 50) String taxId,
            @Size(max = 1000) String notes) {
    }

    public record PartyResponse(Long id, String name, String phone, String email, String address, String taxId,
                                String notes, boolean archived, Instant createdAt) {
    }
}
