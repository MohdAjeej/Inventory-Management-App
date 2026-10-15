package com.b2binventory.repository;

import com.b2binventory.entity.Sale;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    @EntityGraph(attributePaths = {"customer", "actor", "actor.user", "cancelledBy", "cancelledBy.user"})
    Optional<Sale> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Sale> findByBusinessIdAndClientRequestId(Long businessId, UUID clientRequestId);

    boolean existsByBusinessIdAndInvoiceNumberIgnoreCase(Long businessId, String invoiceNumber);

    /** Row lock so concurrent cancellations, returns and payments on one sale serialize. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sale s WHERE s.id = :id AND s.businessId = :businessId")
    Optional<Sale> lock(@Param("id") Long id, @Param("businessId") Long businessId);

    @Override
    @EntityGraph(attributePaths = {"customer", "actor", "actor.user"})
    Page<Sale> findAll(Specification<Sale> spec, Pageable pageable);
}
