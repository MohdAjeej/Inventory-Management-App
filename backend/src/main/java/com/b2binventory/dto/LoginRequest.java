package com.b2binventory.dto;

public record LoginRequest(
    String email,
    String password
) {
}
