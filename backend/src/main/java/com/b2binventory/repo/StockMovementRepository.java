package com.b2binventory.repository;

import com.b2binventory.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository
        extends JpaRepository<StockMovement, Long> {

    List<StockMovement>
    findByProductIdOrderByCreatedAtDesc(Long productId);
}