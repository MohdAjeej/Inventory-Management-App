package com.b2binventory.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class InsufficientStockException extends ApiException {

    public InsufficientStockException(BigDecimal available, BigDecimal requested) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_STOCK",
                "Insufficient stock: available " + available.stripTrailingZeros().toPlainString()
                        + ", requested " + requested.stripTrailingZeros().toPlainString());
    }

    /** Same error naming the product, for documents with several lines. */
    public InsufficientStockException(String productName, InsufficientStockException cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_STOCK",
                "Not enough stock for " + productName + " (" + cause.getMessage().replace("Insufficient stock: ", "") + ")");
    }
}
