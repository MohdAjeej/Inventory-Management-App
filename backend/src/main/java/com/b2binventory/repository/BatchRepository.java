package com.b2binventory.repository;

import com.b2binventory.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    @Query("select b from Batch b where b.variant.id = ?1 and lower(b.lotNumber) = lower(?2)")
    Optional<Batch> findByVariantAndLot(Long variantId, String lotNumber);

    Optional<Batch> findByIdAndBusinessId(Long id, Long businessId);

    List<Batch> findByVariantIdOrderByExpiryDateAsc(Long variantId);
}
