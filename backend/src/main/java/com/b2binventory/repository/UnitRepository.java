package com.b2binventory.repository;

import com.b2binventory.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    Optional<Unit> findByIdAndBusinessId(Long id, Long businessId);

    List<Unit> findByBusinessIdOrderByNameAsc(Long businessId);

    List<Unit> findByBusinessIdAndArchivedFalseOrderByNameAsc(Long businessId);

    Optional<Unit> findByBusinessIdAndCodeIgnoreCase(Long businessId, String code);
}
