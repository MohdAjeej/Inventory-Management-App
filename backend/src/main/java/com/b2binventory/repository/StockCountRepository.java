package com.b2binventory.repository;

import com.b2binventory.entity.StockCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockCountRepository extends JpaRepository<StockCount, Long> {

    @EntityGraph(attributePaths = {"warehouse", "createdBy", "createdBy.user"})
    Optional<StockCount> findByIdAndBusinessId(Long id, Long businessId);

    @EntityGraph(attributePaths = {"warehouse", "createdBy", "createdBy.user"})
    Page<StockCount> findByBusinessIdOrderByCreatedAtDesc(Long businessId, Pageable pageable);
}
