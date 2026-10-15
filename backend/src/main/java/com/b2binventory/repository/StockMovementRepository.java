package com.b2binventory.repository;

import com.b2binventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

    @Override
    @EntityGraph(attributePaths = {"variant", "product", "product.baseUnit", "warehouse", "batch", "actor", "actor.user"})
    Page<StockMovement> findAll(Specification<StockMovement> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"variant", "product", "product.baseUnit", "warehouse", "batch", "actor", "actor.user"})
    List<StockMovement> findAll(Specification<StockMovement> spec, org.springframework.data.domain.Sort sort);

    @EntityGraph(attributePaths = {"variant", "product", "product.baseUnit", "warehouse", "batch", "actor", "actor.user"})
    Optional<StockMovement> findByBusinessIdAndClientRequestId(Long businessId, UUID clientRequestId);

    @EntityGraph(attributePaths = {"variant", "product", "product.baseUnit", "warehouse", "batch", "actor", "actor.user"})
    List<StockMovement> findByBusinessIdAndTransferGroupIdOrderByIdAsc(Long businessId, UUID transferGroupId);
}
