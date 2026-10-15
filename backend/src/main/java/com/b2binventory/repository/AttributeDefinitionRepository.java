package com.b2binventory.repository;

import com.b2binventory.entity.AttributeDefinition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, Long> {

    @EntityGraph(attributePaths = "category")
    Optional<AttributeDefinition> findByIdAndBusinessId(Long id, Long businessId);

    @EntityGraph(attributePaths = "category")
    @Query("select a from AttributeDefinition a where a.businessId = ?1 order by a.sortOrder, a.id")
    List<AttributeDefinition> findAllForBusiness(Long businessId);

    /** Business-wide fields plus the fields of the given categories (the category and its ancestors). */
    @EntityGraph(attributePaths = "category")
    @Query("select a from AttributeDefinition a where a.businessId = ?1 and a.archived = false and "
            + "(a.category is null or a.category.id in ?2) order by a.sortOrder, a.id")
    List<AttributeDefinition> findApplicable(Long businessId, Collection<Long> categoryIds);

    @EntityGraph(attributePaths = "category")
    @Query("select a from AttributeDefinition a where a.businessId = ?1 and a.archived = false and a.category is null "
            + "order by a.sortOrder, a.id")
    List<AttributeDefinition> findGlobal(Long businessId);
}
