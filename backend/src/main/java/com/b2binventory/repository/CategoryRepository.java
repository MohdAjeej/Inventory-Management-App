package com.b2binventory.repository;

import com.b2binventory.domain.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
    // Find all categories by business ID
    List<Category> findByBusinessId(Long businessId);
    
    // Find categories by business ID with pagination
    Page<Category> findByBusinessId(Long businessId, Pageable pageable);
    
    // Find category by ID and business ID
    Optional<Category> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Category> findByBusinessIdAndNameIgnoreCase(Long businessId, String name);
    
    // Search categories by name or description
    @Query("SELECT c FROM Category c WHERE c.business.id = :businessId AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Category> searchCategories(@Param("businessId") Long businessId, 
                                    @Param("query") String query, 
                                    Pageable pageable);
    
    // Count categories by business ID
    long countByBusinessId(Long businessId);
    
    // Count active categories by business ID
    long countByBusinessIdAndStatus(Long businessId, String status);
    
    // Find categories by status
    Page<Category> findByBusinessIdAndStatus(Long businessId, String status, Pageable pageable);
    
    // Check if category name exists for business
    boolean existsByNameAndBusinessId(String name, Long businessId);
    
    // Count categories with products
    @Query("SELECT COUNT(DISTINCT c) FROM Category c JOIN c.products p WHERE c.business.id = :businessId")
    long countCategoriesWithProducts(@Param("businessId") Long businessId);
}
