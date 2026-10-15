package com.b2binventory.dto;

public record RegisterBusinessRequest(
    String businessName,
    String businessType,
    String businessMobile,
    String businessEmail,
    String address,
    String city,
    String state,
    String country,
    String gstNumber,
    String name,
    String email,
    String password
) {
}
