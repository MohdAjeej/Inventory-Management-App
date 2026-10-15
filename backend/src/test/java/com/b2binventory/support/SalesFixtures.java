package com.b2binventory.support;

import java.time.Instant;
import java.util.*;

/** Request builders for sales, purchases and returns. */
public final class SalesFixtures {

    private SalesFixtures() {
    }

    public static Map<String, Object> pricedProduct(String name, Object openingQty, Object purchasePrice, Object salePrice) {
        Map<String, Object> body = Fixtures.product(name, openingQty);
        if (purchasePrice != null) body.put("purchasePrice", purchasePrice);
        if (salePrice != null) body.put("salePrice", salePrice);
        return body;
    }

    public static Map<String, Object> line(long variantId, Object qty, Object price) {
        Map<String, Object> l = new HashMap<>();
        l.put("variantId", variantId);
        l.put("quantity", qty);
        l.put("unitPrice", price);
        return l;
    }

    public static Map<String, Object> line(long variantId, Object qty, Object price, Object discount, Object taxRate) {
        Map<String, Object> l = line(variantId, qty, price);
        l.put("discount", discount);
        l.put("taxRate", taxRate);
        return l;
    }

    @SafeVarargs
    public static Map<String, Object> sale(Map<String, Object>... lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(lines));
        body.put("clientRequestId", UUID.randomUUID().toString());
        return body;
    }

    public static Map<String, Object> sale(Instant soldAt, List<Map<String, Object>> lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", lines);
        body.put("soldAt", soldAt.toString());
        body.put("clientRequestId", UUID.randomUUID().toString());
        return body;
    }

    public static Map<String, Object> paid(Map<String, Object> sale, String method, Object amount) {
        sale.put("payment", Map.of("method", method, "amount", amount));
        return sale;
    }

    public static Map<String, Object> payment(Object amount, String method) {
        return Map.of("amount", amount, "method", method, "clientRequestId", UUID.randomUUID().toString());
    }

    public static Map<String, Object> returnOf(long saleItemId, Object qty, boolean resalable, Object refund, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(Map.of("saleItemId", saleItemId, "quantity", qty, "resalable", resalable)));
        body.put("reason", reason);
        if (refund != null) body.put("refundAmount", refund);
        body.put("clientRequestId", UUID.randomUUID().toString());
        return body;
    }

    public static Map<String, Object> purchase(long variantId, Object qty, Object unitCost) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(Map.of("variantId", variantId, "quantity", qty, "unitCost", unitCost)));
        body.put("supplierName", "Acme Traders");
        body.put("saveSupplier", true);
        body.put("clientRequestId", UUID.randomUUID().toString());
        return body;
    }
}
