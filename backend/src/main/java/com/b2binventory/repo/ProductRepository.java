package com.b2binventory.repository;

import com.b2binventory.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    List<Product>
    findByBusinessIdAndActiveTrueOrderByUpdatedAtDesc(
            Long businessId
    );

    List<Product>
    findByBusinessIdAndCategoryIgnoreCaseAndActiveTrueOrderByUpdatedAtDesc(
            Long businessId,
            String category
    );

    List<Product>
    findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
            Long businessId,
            String name
    );
}