package com.b2binventory.repository;

import com.b2binventory.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    Optional<Unit> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Unit> findByBusinessIdAndCodeIgnoreCase(Long businessId, String code);

    /** Standard units and the business's own units. */
    @Query("select u from Unit u where (u.businessId = :businessId or u.businessId is null)"
            + " and (:includeArchived = true or u.archived = false) order by u.category, u.name")
    List<Unit> findVisible(@Param("businessId") Long businessId, @Param("includeArchived") boolean includeArchived);

    @Query("select u from Unit u where u.id = :id and (u.businessId = :businessId or u.businessId is null)")
    Optional<Unit> findVisibleById(@Param("id") Long id, @Param("businessId") Long businessId);

    /** Units with this code the business can use, its own unit first. */
    @Query("select u from Unit u where lower(u.code) = lower(:code) and (u.businessId = :businessId or u.businessId is null)"
            + " order by case when u.businessId is null then 1 else 0 end")
    List<Unit> findVisibleByCode(@Param("businessId") Long businessId, @Param("code") String code);

    @Query("select u from Unit u where lower(u.name) = lower(:name) and (u.businessId = :businessId or u.businessId is null)")
    List<Unit> findVisibleByName(@Param("businessId") Long businessId, @Param("name") String name);
}
