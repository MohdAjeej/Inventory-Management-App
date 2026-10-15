package com.b2binventory.repository;

import com.b2binventory.entity.SaleItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    @EntityGraph(attributePaths = {"product", "product.baseUnit", "variant", "warehouse", "batch"})
    @Query("select i from SaleItem i where i.sale.id in ?1 order by i.sale.id, i.lineNo")
    List<SaleItem> findBySaleIds(Collection<Long> saleIds);
}
