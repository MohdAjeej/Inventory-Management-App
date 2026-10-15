package com.b2binventory.repository;

import com.b2binventory.entity.StockBalance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {

    /** Row-level lock (SELECT ... FOR UPDATE) so concurrent operations on one balance serialize. */
    @Query(value = "SELECT * FROM stock_balances WHERE variant_id = ?1 AND warehouse_id = ?2 "
            + "AND COALESCE(batch_id, 0) = COALESCE(CAST(?3 AS BIGINT), 0) FOR UPDATE", nativeQuery = true)
    Optional<StockBalance> lock(Long variantId, Long warehouseId, Long batchId);

    /** Creates the balance row if missing; the unique index makes concurrent inserts safe. */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO stock_balances (business_id, variant_id, warehouse_id, batch_id) "
            + "VALUES (?1, ?2, ?3, CAST(?4 AS BIGINT)) ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertIfMissing(Long businessId, Long variantId, Long warehouseId, Long batchId);

    /** Total quantity on hand of a product across its active variants and all warehouses. */
    @Query("select coalesce(sum(b.onHand), 0) from StockBalance b where b.variant.product.id = ?1 and b.variant.archived = false")
    java.math.BigDecimal onHandForProduct(Long productId);

    @EntityGraph(attributePaths = {"warehouse", "batch", "variant"})
    @Query("select b from StockBalance b where b.variant.product.id = ?1 order by b.warehouse.name, b.id")
    List<StockBalance> findByProduct(Long productId);

    @EntityGraph(attributePaths = {"warehouse", "batch", "variant"})
    @Query("select b from StockBalance b where b.variant.id = ?1 order by b.warehouse.name, b.id")
    List<StockBalance> findByVariant(Long variantId);

    @EntityGraph(attributePaths = {"variant", "batch"})
    @Query("select b from StockBalance b where b.warehouse.id = ?1")
    List<StockBalance> findByWarehouse(Long warehouseId);

    @Query("select b.variant.id, sum(b.onHand), sum(b.reserved), sum(b.damaged) from StockBalance b "
            + "where b.variant.id in ?1 group by b.variant.id")
    List<Object[]> totalsForVariants(Collection<Long> variantIds);

    @Query("select b.variant.id, sum(b.onHand), sum(b.reserved), sum(b.damaged) from StockBalance b "
            + "where b.variant.id in ?1 and b.warehouse.id = ?2 group by b.variant.id")
    List<Object[]> totalsForVariantsInWarehouse(Collection<Long> variantIds, Long warehouseId);

    /** warehouseId, number of products with stock, total quantity on hand. */
    @Query("select b.warehouse.id, count(distinct b.variant.product.id), sum(b.onHand) from StockBalance b "
            + "where b.businessId = ?1 and b.onHand <> 0 and b.variant.archived = false "
            + "and b.variant.product.archived = false group by b.warehouse.id")
    List<Object[]> warehouseTotals(Long businessId);

    @Query("select count(b) > 0 from StockBalance b where b.warehouse.id = ?1 and "
            + "(b.onHand <> 0 or b.reserved <> 0 or b.damaged <> 0)")
    boolean warehouseHasStock(Long warehouseId);

    @Query("select count(b) > 0 from StockBalance b where b.variant.id = ?1 and "
            + "(b.onHand <> 0 or b.reserved <> 0 or b.damaged <> 0)")
    boolean variantHasStock(Long variantId);

    @Query("select count(b) > 0 from StockBalance b where b.variant.product.id = ?1 and "
            + "(b.onHand <> 0 or b.reserved <> 0 or b.damaged <> 0)")
    boolean productHasStock(Long productId);
}
