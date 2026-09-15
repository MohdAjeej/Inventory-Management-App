package com.b2binventory.repository;

import com.b2binventory.domain.StockMovement;
import com.b2binventory.domain.StockMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    
    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId);
    
    List<StockMovement> findByBusinessIdOrderByCreatedAtDesc(Long businessId);
    
    List<StockMovement> findByBusinessIdAndTypeOrderByCreatedAtDesc(Long businessId, StockMovementType type);
    
    List<StockMovement> findByBusinessIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        Long businessId, Instant startDate, Instant endDate);
    
    @Query("SELECT sm FROM StockMovement sm WHERE sm.business.id = ?1 AND sm.user.id = ?2 ORDER BY sm.createdAt DESC")
    List<StockMovement> findByBusinessIdAndUserId(Long businessId, Long userId);
}
