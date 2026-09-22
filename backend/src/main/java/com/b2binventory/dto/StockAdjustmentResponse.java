package com.b2binventory.dto;

import com.b2binventory.domain.StockAdjustment;

import java.time.Instant;

public class StockAdjustmentResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String type;
    private Integer quantity;
    private Integer stockBefore;
    private Integer stockAfter;
    private String reason;
    private String notes;
    private String addedBy;
    private Instant adjustedAt;

    public static StockAdjustmentResponse from(StockAdjustment adjustment) {
        StockAdjustmentResponse response = new StockAdjustmentResponse();
        response.setId(adjustment.getId());
        response.setProductId(adjustment.getProduct().getId());
        response.setProductName(adjustment.getProduct().getName());
        response.setType(adjustment.getType().name());
        response.setQuantity(adjustment.getQuantity());
        response.setStockBefore(adjustment.getStockBefore());
        response.setStockAfter(adjustment.getStockAfter());
        response.setReason(adjustment.getReason());
        response.setNotes(adjustment.getNotes());
        response.setAddedBy(adjustment.getUser().getName());
        response.setAdjustedAt(adjustment.getAdjustedAt());
        return response;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getStockBefore() {
        return stockBefore;
    }

    public void setStockBefore(Integer stockBefore) {
        this.stockBefore = stockBefore;
    }

    public Integer getStockAfter() {
        return stockAfter;
    }

    public void setStockAfter(Integer stockAfter) {
        this.stockAfter = stockAfter;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public Instant getAdjustedAt() {
        return adjustedAt;
    }

    public void setAdjustedAt(Instant adjustedAt) {
        this.adjustedAt = adjustedAt;
    }
}
