package com.b2binventory.repository;

import com.b2binventory.entity.ProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductMappingRepository extends JpaRepository<ProductMapping, Long> {

    Optional<ProductMapping> findByBuyerBusinessIdAndSellerVariantId(Long buyerBusinessId, Long sellerVariantId);

    Optional<ProductMapping> findByIdAndBuyerBusinessId(Long id, Long buyerBusinessId);

    List<ProductMapping> findByBuyerBusinessIdOrderByUpdatedAtDesc(Long buyerBusinessId);
}
