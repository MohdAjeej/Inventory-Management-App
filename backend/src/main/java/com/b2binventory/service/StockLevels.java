package com.b2binventory.service;

import com.b2binventory.dto.ProductDtos.StockStatus;
import com.b2binventory.dto.ProductDtos.StockTotals;
import com.b2binventory.entity.BusinessSettings;
import com.b2binventory.entity.Product;
import com.b2binventory.entity.ProductVariant;
import com.b2binventory.repository.StockBalanceRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Aggregates balances and derives stock status from thresholds. */
@Component
public class StockLevels {

    private final StockBalanceRepository balances;

    public StockLevels(StockBalanceRepository balances) {
        this.balances = balances;
    }

    public Map<Long, StockTotals> byVariant(Collection<Long> variantIds, Long warehouseId) {
        Map<Long, StockTotals> result = new HashMap<>();
        if (variantIds.isEmpty()) return result;
        List<Object[]> rows = warehouseId == null
                ? balances.totalsForVariants(variantIds)
                : balances.totalsForVariantsInWarehouse(variantIds, warehouseId);
        for (Object[] r : rows) {
            BigDecimal onHand = (BigDecimal) r[1];
            BigDecimal reserved = (BigDecimal) r[2];
            BigDecimal damaged = (BigDecimal) r[3];
            result.put((Long) r[0], new StockTotals(onHand, reserved, damaged, onHand.subtract(reserved).subtract(damaged)));
        }
        return result;
    }

    public static StockTotals sum(Collection<StockTotals> totals) {
        BigDecimal onHand = BigDecimal.ZERO, reserved = BigDecimal.ZERO, damaged = BigDecimal.ZERO, available = BigDecimal.ZERO;
        for (StockTotals t : totals) {
            onHand = onHand.add(t.onHand());
            reserved = reserved.add(t.reserved());
            damaged = damaged.add(t.damaged());
            available = available.add(t.available());
        }
        return new StockTotals(onHand, reserved, damaged, available);
    }

    public static int threshold(Product product, BusinessSettings settings) {
        return product.getMinStock() != null ? product.getMinStock() : settings.getDefaultLowStockThreshold();
    }

    public static int threshold(ProductVariant variant, BusinessSettings settings) {
        if (variant.getMinStock() != null) return variant.getMinStock();
        return threshold(variant.getProduct(), settings);
    }

    public static StockStatus status(BigDecimal available, int threshold) {
        if (available.signum() <= 0) return StockStatus.OUT_OF_STOCK;
        if (available.compareTo(BigDecimal.valueOf(threshold)) <= 0) return StockStatus.LOW_STOCK;
        return StockStatus.IN_STOCK;
    }
}
