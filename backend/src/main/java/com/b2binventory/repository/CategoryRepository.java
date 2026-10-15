package com.b2binventory.repository;

import com.b2binventory.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndBusinessId(Long id, Long businessId);

    @Query("select c from Category c left join fetch c.parent where c.businessId = ?1 order by c.name")
    List<Category> findAllForBusiness(Long businessId);

    boolean existsByParentIdAndArchivedFalse(Long parentId);

    @Query("select c.id, count(p) from Product p join p.category c where p.businessId = ?1 and p.archived = false group by c.id")
    List<Object[]> productCounts(Long businessId);
}
