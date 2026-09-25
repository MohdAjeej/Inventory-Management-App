package com.b2binventory.repository;

import com.b2binventory.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    List<Product> findByBusinessIdOrderByUpdatedAtDesc(Long businessId);
    
    List<Product> findByBusinessIdAndCategoryNameIgnoreCaseOrderByUpdatedAtDesc(Long businessId, String categoryName);
    
    List<Product> findByBusinessIdAndNameContainingIgnoreCaseOrderByName(Long businessId, String name);
    
    List<Product> findByBusinessIdAndActiveOrderByUpdatedAtDesc(Long businessId, boolean active);
    
    Optional<Product> findByBusinessIdAndSku(Long businessId, String sku);
    
    Optional<Product> findByBusinessIdAndBarcode(Long businessId, String barcode);

    long countByBusinessId(Long businessId);
    
    @Query("SELECT p FROM Product p WHERE p.business.id = ?1 AND p.quantity <= p.minimumStock AND p.active = true ORDER BY p.quantity ASC")
    List<Product> findLowStockProducts(Long businessId);
    
    @Query("SELECT p FROM Product p WHERE p.business.id = ?1 AND p.quantity = 0 AND p.active = true ORDER BY p.updatedAt DESC")
    List<Product> findOutOfStockProducts(Long businessId);
}
