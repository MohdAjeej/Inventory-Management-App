package com.b2binventory.repository;

import com.b2binventory.entity.UnitConversion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnitConversionRepository extends JpaRepository<UnitConversion, Long> {

    @EntityGraph(attributePaths = {"fromUnit", "toUnit", "product"})
    List<UnitConversion> findByBusinessIdOrderByIdAsc(Long businessId);

    @EntityGraph(attributePaths = {"fromUnit", "toUnit", "product"})
    Optional<UnitConversion> findByIdAndBusinessId(Long id, Long businessId);

    @EntityGraph(attributePaths = {"fromUnit", "toUnit", "product"})
    List<UnitConversion> findByBusinessIdAndFromUnitIdAndToUnitId(Long businessId, Long fromUnitId, Long toUnitId);
}
