package com.b2binventory.repository;

import com.b2binventory.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByIdAndBusinessId(Long id, Long businessId);

    List<Warehouse> findByBusinessIdOrderByNameAsc(Long businessId);

    List<Warehouse> findByBusinessIdAndArchivedFalseOrderByNameAsc(Long businessId);

    Optional<Warehouse> findFirstByBusinessIdAndDefaultWarehouseTrue(Long businessId);
}
