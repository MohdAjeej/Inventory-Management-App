package com.b2binventory.dto;

public record ProductRequest(
    String category,
    String name,
    String brand,
    String sku,
    String barcode,
    String unit,
    String size,
    String thickness,
    String color,
    String model,
    String description,
    int quantity,
    int minimumStock,
    String imageUrl
) {
}
