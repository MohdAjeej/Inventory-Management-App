package com.b2binventory.repository;

import com.b2binventory.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"category", "baseUnit"})
    Optional<Product> findByIdAndBusinessId(Long id, Long businessId);

    @Override
    @EntityGraph(attributePaths = {"category", "baseUnit"})
    Page<Product> findAll(org.springframework.data.jpa.domain.Specification<Product> spec, Pageable pageable);

    long countByBusinessIdAndArchivedFalse(Long businessId);

    boolean existsByBaseUnitId(Long unitId);
}
