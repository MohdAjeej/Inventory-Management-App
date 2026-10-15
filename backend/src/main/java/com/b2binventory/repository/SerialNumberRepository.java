package com.b2binventory.repository;

import com.b2binventory.entity.SerialNumber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SerialNumberRepository extends JpaRepository<SerialNumber, Long> {

    List<SerialNumber> findByBusinessIdAndVariantIdAndSerialIn(Long businessId, Long variantId, Collection<String> serials);

    List<SerialNumber> findByVariantIdOrderBySerialAsc(Long variantId);
}
