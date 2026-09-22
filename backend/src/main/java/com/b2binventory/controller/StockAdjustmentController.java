package com.b2binventory.controller;

import com.b2binventory.domain.AppUser;
import com.b2binventory.domain.Business;
import com.b2binventory.domain.Product;
import com.b2binventory.domain.StockAdjustment;
import com.b2binventory.dto.StockAdjustmentRequest;
import com.b2binventory.dto.StockAdjustmentResponse;
import com.b2binventory.repository.BusinessRepository;
import com.b2binventory.repository.ProductRepository;
import com.b2binventory.repository.StockAdjustmentRepository;
import com.b2binventory.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stock-adjustments")
public class StockAdjustmentController {

    private final StockAdjustmentRepository adjustmentRepository;
    private final ProductRepository productRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;

    public StockAdjustmentController(
            StockAdjustmentRepository adjustmentRepository,
            ProductRepository productRepository,
            BusinessRepository businessRepository,
            UserRepository userRepository) {
        this.adjustmentRepository = adjustmentRepository;
        this.productRepository = productRepository;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> adjustStock(@RequestBody StockAdjustmentRequest request) {
        try {
            // Validate required fields
            if (request.getProductId() == null || request.getBusinessId() == null || 
                request.getUserId() == null || request.getType() == null || 
                request.getQuantity() == null || request.getReason() == null) {
                return ResponseEntity.badRequest().body("Missing required fields");
            }

            // Find entities
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));
            Business business = businessRepository.findById(request.getBusinessId())
                    .orElseThrow(() -> new RuntimeException("Business not found"));
            AppUser user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Create adjustment record
            StockAdjustment adjustment = new StockAdjustment();
            adjustment.setProduct(product);
            adjustment.setBusiness(business);
            adjustment.setUser(user);
            adjustment.setType(StockAdjustment.AdjustmentType.valueOf(request.getType()));
            adjustment.setQuantity(request.getQuantity());
            adjustment.setStockBefore(product.getQuantity());
            adjustment.setReason(request.getReason());
            adjustment.setNotes(request.getNotes());

            // Update product quantity
            int newQuantity;
            if ("ADDED".equals(request.getType())) {
                newQuantity = product.getQuantity() + request.getQuantity();
            } else if ("REDUCED".equals(request.getType())) {
                newQuantity = product.getQuantity() - request.getQuantity();
                if (newQuantity < 0) {
                    return ResponseEntity.badRequest().body("Insufficient stock");
                }
            } else {
                return ResponseEntity.badRequest().body("Invalid adjustment type");
            }

            product.setQuantity(newQuantity);
            product.touch();
            adjustment.setStockAfter(newQuantity);

            // Save
            productRepository.save(product);
            StockAdjustment saved = adjustmentRepository.save(adjustment);

            return ResponseEntity.ok(StockAdjustmentResponse.from(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<StockAdjustmentResponse>> getProductHistory(
            @PathVariable Long productId,
            @RequestParam(required = false) String type) {
        
        List<StockAdjustment> adjustments;
        if (type != null && !type.isEmpty()) {
            adjustments = adjustmentRepository.findByProductIdAndType(
                    productId, 
                    StockAdjustment.AdjustmentType.valueOf(type)
            );
        } else {
            adjustments = adjustmentRepository.findByProductIdOrderByAdjustedAtDesc(productId);
        }

        List<StockAdjustmentResponse> response = adjustments.stream()
                .map(StockAdjustmentResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/business/{businessId}")
    public ResponseEntity<List<StockAdjustmentResponse>> getBusinessHistory(
            @PathVariable Long businessId) {
        
        List<StockAdjustment> adjustments = adjustmentRepository
                .findByBusinessIdOrderByAdjustedAtDesc(businessId);

        List<StockAdjustmentResponse> response = adjustments.stream()
                .map(StockAdjustmentResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/business/{businessId}/summary")
    public ResponseEntity<Map<String, Object>> getBusinessSummary(
            @PathVariable Long businessId) {
        
        List<StockAdjustment> adjustments = adjustmentRepository
                .findByBusinessIdOrderByAdjustedAtDesc(businessId);
        
        int totalInward = adjustments.stream()
                .filter(a -> a.getType() == StockAdjustment.AdjustmentType.ADDED)
                .mapToInt(StockAdjustment::getQuantity)
                .sum();
        
        int totalOutward = adjustments.stream()
                .filter(a -> a.getType() == StockAdjustment.AdjustmentType.REDUCED)
                .mapToInt(StockAdjustment::getQuantity)
                .sum();
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalInward", totalInward);
        summary.put("totalOutward", totalOutward);
        summary.put("netIncrease", totalInward - totalOutward);
        summary.put("totalEntries", adjustments.size());
        
        return ResponseEntity.ok(summary);
    }
}
