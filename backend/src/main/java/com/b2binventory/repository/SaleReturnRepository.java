package com.b2binventory.repository;

import com.b2binventory.entity.SaleReturn;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {

    Optional<SaleReturn> findByBusinessIdAndClientRequestId(Long businessId, UUID clientRequestId);

    @EntityGraph(attributePaths = {"actor", "actor.user", "items", "items.saleItem", "items.saleItem.product"})
    List<SaleReturn> findBySaleIdOrderByIdAsc(Long saleId);
}
