package com.b2binventory.repository;

import com.b2binventory.entity.ProductVariant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @EntityGraph(attributePaths = {"product", "product.baseUnit"})
    Optional<ProductVariant> findByIdAndBusinessId(Long id, Long businessId);

    List<ProductVariant> findByProductIdOrderByIdAsc(Long productId);

    @Query("select v from ProductVariant v where v.product.id in ?1 order by v.id")
    List<ProductVariant> findForProducts(Collection<Long> productIds);

    @EntityGraph(attributePaths = {"product", "product.baseUnit"})
    @Query("select v from ProductVariant v where v.businessId = ?1 and v.archived = false and v.product.archived = false "
            + "and (?3 = true or v.product.category.id in ?2) order by v.product.name, v.name")
    List<ProductVariant> findActiveForCount(Long businessId, Collection<Long> categoryIds, boolean allCategories);

    @EntityGraph(attributePaths = {"product", "product.baseUnit"})
    @Query("select v from ProductVariant v where v.businessId = ?1 and (lower(v.sku) = lower(?2) or v.barcode = ?2)")
    List<ProductVariant> findByCode(Long businessId, String code);

    @Query("select count(v) > 0 from ProductVariant v where v.businessId = ?1 and lower(v.sku) = lower(?2) and v.id <> ?3")
    boolean skuTaken(Long businessId, String sku, Long excludeId);

    @Query("select count(v) > 0 from ProductVariant v where v.businessId = ?1 and v.barcode = ?2 and v.id <> ?3")
    boolean barcodeTaken(Long businessId, String barcode, Long excludeId);
}
