package com.b2binventory.dto;

import com.b2binventory.domain.StockMovementType;

public record StockRequest(
    StockMovementType type,
    int quantity,
    String reason
) {
}
