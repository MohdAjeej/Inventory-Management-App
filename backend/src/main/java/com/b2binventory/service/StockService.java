package com.b2binventory.service;

import com.b2binventory.domain.*;
import com.b2binventory.dto.StockRequest;
import com.b2binventory.repository.ProductRepository;
import com.b2binventory.repository.StockMovementRepository;
import com.b2binventory.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class StockService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;

    public StockService(ProductRepository productRepository,
                        UserRepository userRepository,
                        StockMovementRepository stockMovementRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional
    public Map<String, Object> adjustStock(Long productId, Long businessId, Long userId, StockRequest request) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!product.getBusiness().getId().equals(businessId) || 
            !user.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("Access denied.");
        }

        if (request.quantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        int oldQuantity = product.getQuantity();
        int newQuantity;

        if (request.type() == StockMovementType.ADD) {
            newQuantity = oldQuantity + request.quantity();
        } else {
            newQuantity = oldQuantity - request.quantity();
        }

        if (newQuantity < 0) {
            throw new IllegalArgumentException("Stock cannot be less than zero.");
        }

        product.setQuantity(newQuantity);
        product.touch();
        productRepository.save(product);

        createStockMovement(
            product.getBusiness(),
            user,
            product,
            request.type(),
            request.quantity(),
            oldQuantity,
            newQuantity,
            request.reason()
        );

        return Map.of(
            "message", "Stock updated successfully.",
            "quantity", newQuantity,
            "previousQuantity", oldQuantity
        );
    }

    public void createStockMovement(Business business, AppUser user, Product product, 
                                     StockMovementType type, int quantity, 
                                     int oldQuantity, int newQuantity, String reason) {
        StockMovement movement = new StockMovement();
        movement.setBusiness(business);
        movement.setUser(user);
        movement.setProduct(product);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setPreviousQuantity(oldQuantity);
        movement.setNewQuantity(newQuantity);
        movement.setReason(reason);
        
        stockMovementRepository.save(movement);
    }

    public List<StockMovement> getProductHistory(Long productId, Long businessId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        
        if (!product.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("Access denied.");
        }
        
        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    public List<StockMovement> getBusinessStockHistory(Long businessId) {
        return stockMovementRepository.findByBusinessIdOrderByCreatedAtDesc(businessId);
    }

    public List<StockMovement> getStockHistoryByDateRange(Long businessId, Instant startDate, Instant endDate) {
        return stockMovementRepository.findByBusinessIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            businessId, startDate, endDate
        );
    }

    public List<StockMovement> getUserStockHistory(Long businessId, Long userId) {
        return stockMovementRepository.findByBusinessIdAndUserId(businessId, userId);
    }
}
