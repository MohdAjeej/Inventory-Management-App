package com.b2binventory.repository;

import com.b2binventory.entity.Purchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, Long>, JpaSpecificationExecutor<Purchase> {

    @EntityGraph(attributePaths = {"supplier", "actor", "actor.user", "cancelledBy", "cancelledBy.user",
            "items", "items.product", "items.product.baseUnit", "items.variant", "items.warehouse", "items.batch"})
    Optional<Purchase> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Purchase> findByBusinessIdAndClientRequestId(Long businessId, UUID clientRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Purchase p WHERE p.id = :id AND p.businessId = :businessId")
    Optional<Purchase> lock(@Param("id") Long id, @Param("businessId") Long businessId);

    @Override
    @EntityGraph(attributePaths = {"supplier", "actor", "actor.user"})
    Page<Purchase> findAll(Specification<Purchase> spec, Pageable pageable);
}
