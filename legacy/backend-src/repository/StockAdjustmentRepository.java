package com.b2binventory.repository;

import com.b2binventory.domain.StockAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {
    
    List<StockAdjustment> findByProductIdOrderByAdjustedAtDesc(Long productId);
    
    List<StockAdjustment> findByBusinessIdOrderByAdjustedAtDesc(Long businessId);
    
    @Query("SELECT sa FROM StockAdjustment sa WHERE sa.product.id = :productId AND " +
           "(:type IS NULL OR sa.type = :type) ORDER BY sa.adjustedAt DESC")
    List<StockAdjustment> findByProductIdAndType(
            @Param("productId") Long productId, 
            @Param("type") StockAdjustment.AdjustmentType type
    );
}
