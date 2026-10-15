package com.b2binventory.repository;

import com.b2binventory.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    @Query("select i from ProductImage i where i.product.id in ?1 order by i.sortOrder, i.id")
    List<ProductImage> findForProducts(Collection<Long> productIds);

    Optional<ProductImage> findByIdAndBusinessId(Long id, Long businessId);

    long countByProductId(Long productId);
}
