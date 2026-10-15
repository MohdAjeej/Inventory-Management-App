package com.b2binventory;

import com.b2binventory.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.b2binventory.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Business B must never see or change business A's data, whatever ids it sends. */
class TenantIsolationTest extends IntegrationTest {

    @Test
    void oneBusinessCannotReachAnothersRecords() {
        Session a = registerBusiness("Tenant A");
        Session b = registerBusiness("Tenant B");

        long category = post(a, "/api/categories", Map.of("name", "A only")).get("id").asLong();
        Response product = post(a, "/api/products", Map.of("name", "Secret", "categoryId", category,
                "sku", "SECRET-1", "openingStock", Map.of("quantity", 5)));
        long productId = product.get("id").asLong();
        long variant = defaultVariant(product.body());
        long warehouseA = warehouseId(this, a, "Main Warehouse");
        long warehouseB = warehouseId(this, b, "Main Warehouse");

        // Direct reads by id
        assertThat(get(b, "/api/products/" + productId).status()).isEqualTo(404);
        assertThat(get(b, "/api/categories/" + category).status()).isEqualTo(404);
        assertThat(get(b, "/api/warehouses/" + warehouseA).status()).isEqualTo(404);
        assertThat(get(b, "/api/variants/" + variant + "/balances").status()).isEqualTo(404);
        assertThat(get(b, "/api/form-schema?categoryId=" + category).status()).isEqualTo(404);

        // Writes using A's ids
        assertThat(put(b, "/api/products/" + productId, Map.of("name", "Hacked")).status()).isEqualTo(404);
        assertThat(post(b, "/api/products/" + productId + "/archive", null).status()).isEqualTo(404);
        assertThat(post(b, "/api/stock/operations", op("REDUCE", variant, 1)).status()).isEqualTo(404);
        assertThat(post(b, "/api/stock/transfers", Map.of("variantId", variant, "fromWarehouseId", warehouseA,
                "toWarehouseId", warehouseB, "quantity", 1)).status()).isEqualTo(404);
        assertThat(post(b, "/api/products", Map.of("name", "X", "categoryId", category)).status()).isEqualTo(404);
        assertThat(post(b, "/api/stock-counts", Map.of("warehouseId", warehouseA)).status()).isEqualTo(404);

        // Lists, search, lookup, reports and history never include A's rows
        assertThat(get(b, "/api/products").get("totalElements").asInt()).isZero();
        assertThat(get(b, "/api/products?q=Secret").get("totalElements").asInt()).isZero();
        assertThat(get(b, "/api/products/lookup?code=SECRET-1").body()).isEmpty();
        assertThat(get(b, "/api/categories").body()).isEmpty();
        assertThat(get(b, "/api/stock/movements").get("totalElements").asInt()).isZero();
        assertThat(get(b, "/api/stock/movements?productId=" + productId).get("totalElements").asInt()).isZero();
        assertThat(get(b, "/api/reports/inventory").get("totalElements").asInt()).isZero();
        Response summary = get(b, "/api/reports/movement-summary");
        assertThat(summary.get("movementCount").asInt()).isZero();
        assertThat(summary.get("slowMoving").size()).isZero();
        assertThat(get(b, "/api/dashboard").get("activeProducts").asInt()).isZero();
        assertThat(get(b, "/api/warehouses").body()).hasSize(1);

        // A's data is intact
        Response check = get(a, "/api/products/" + productId);
        assertThat(check.get("name").asText()).isEqualTo("Secret");
        assertThat(available(check)).isEqualTo(5.0);
    }

    @Test
    void sameSkuMayExistInDifferentBusinesses() {
        Session a = registerBusiness("SKU A");
        Session b = registerBusiness("SKU B");
        assertThat(post(a, "/api/products", Map.of("name", "Thing", "sku", "SHARED")).status()).isEqualTo(201);
        assertThat(post(b, "/api/products", Map.of("name", "Thing", "sku", "SHARED")).status()).isEqualTo(201);
    }
}
