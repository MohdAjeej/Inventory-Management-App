package com.b2binventory.repository;

import com.b2binventory.entity.StorageLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {

    List<StorageLocation> findByWarehouseIdOrderByCodeAsc(Long warehouseId);

    Optional<StorageLocation> findByIdAndBusinessId(Long id, Long businessId);
}
