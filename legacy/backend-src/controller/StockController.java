package com.b2binventory.controller;

import com.b2binventory.domain.StockMovement;
import com.b2binventory.dto.StockRequest;
import com.b2binventory.service.StockService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/products/{id}/stock")
    public Map<String, Object> adjustStock(
        @PathVariable Long id,
        @RequestParam Long businessId,
        @RequestParam Long userId,
        @RequestBody StockRequest request
    ) {
        return stockService.adjustStock(id, businessId, userId, request);
    }

    @GetMapping("/products/{id}/history")
    public List<StockMovement> getProductHistory(
        @PathVariable Long id,
        @RequestParam Long businessId
    ) {
        return stockService.getProductHistory(id, businessId);
    }

    @GetMapping("/stock-history")
    public List<StockMovement> getBusinessStockHistory(@RequestParam Long businessId) {
        return stockService.getBusinessStockHistory(businessId);
    }

    @GetMapping("/stock-history/date-range")
    public List<StockMovement> getStockHistoryByDateRange(
        @RequestParam Long businessId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        return stockService.getStockHistoryByDateRange(businessId, startDate, endDate);
    }

    @GetMapping("/stock-history/user/{userId}")
    public List<StockMovement> getUserStockHistory(
        @PathVariable Long userId,
        @RequestParam Long businessId
    ) {
        return stockService.getUserStockHistory(businessId, userId);
    }
}
