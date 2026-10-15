package com.b2binventory;

import com.b2binventory.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static com.b2binventory.support.Fixtures.*;
import static com.b2binventory.support.SalesFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class SalesTest extends IntegrationTest {

    private static double num(JsonNode n, String field) {
        return n.get(field).asDouble();
    }

    @Test
    void productHasPurchaseAndSellingPriceWithProfitCalculations() {
        Session admin = registerBusiness("Price Co");
        Response p = post(admin, "/api/products", pricedProduct("Widget", 100, "80", "120"));
        assertThat(p.status()).isEqualTo(201);
        assertThat(num(p.body(), "purchasePrice")).isEqualTo(80.0);
        assertThat(num(p.body(), "salePrice")).isEqualTo(120.0);
        assertThat(num(p.body(), "averageCost")).isEqualTo(80.0);
        JsonNode pricing = p.get("pricing");
        assertThat(num(pricing, "profitPerUnit")).isEqualTo(40.0);
        assertThat(num(pricing, "potentialProfit")).isEqualTo(4000.0);
        assertThat(num(pricing, "stockValueAtCost")).isEqualTo(8000.0);
        assertThat(num(pricing, "marginPercent")).isEqualTo(33.3);

        Response negative = post(admin, "/api/products", pricedProduct("Bad", null, "-1", "5"));
        assertThat(negative.status()).isEqualTo(400);
        assertThat(negative.get("fieldErrors").has("purchasePrice")).isTrue();

        // Staff cannot see costs or margins.
        Session staff = addEmployee(admin, "STAFF");
        Response seen = get(staff, "/api/products/" + p.get("id").asLong());
        assertThat(seen.body().has("purchasePrice")).isFalse();
        assertThat(seen.body().has("pricing")).isFalse();
        assertThat(num(seen.body(), "salePrice")).isEqualTo(120.0);
    }

    @Test
    void saleReducesStockAndKeepsPriceSnapshotsWhenPricesChange() {
        Session admin = registerBusiness("Snapshot Co");
        Response p = post(admin, "/api/products", pricedProduct("Bulb", 100, "80", "120"));
        long productId = p.get("id").asLong();
        long variant = defaultVariant(p.body());

        Response sold = post(admin, "/api/sales", sale(line(variant, 7, "120")));
        assertThat(sold.status()).isEqualTo(201);
        assertThat(sold.get("invoiceNumber").asText()).startsWith("INV-");
        assertThat(num(sold.body(), "total")).isEqualTo(840.0);
        assertThat(num(sold.body(), "grossProfit")).isEqualTo(280.0);
        assertThat(num(sold.get("stock").get(0), "available")).isEqualTo(93.0);
        assertThat(available(get(admin, "/api/products/" + productId))).isEqualTo(93.0);
        assertThat(types(get(admin, "/api/stock/movements?productId=" + productId).body())).containsExactly("SALE", "OPENING");
        assertThat(sold.get("paymentStatus").asText()).isEqualTo("UNPAID");

        Map<String, Object> update = pricedProduct("Bulb", null, "90", "150");
        assertThat(put(admin, "/api/products/" + productId, update).status()).isEqualTo(200);
        Response again = get(admin, "/api/sales/" + sold.get("id").asLong());
        JsonNode item = again.get("items").get(0);
        assertThat(num(item, "unitPrice")).isEqualTo(120.0);
        assertThat(num(item, "unitCost")).isEqualTo(80.0);
        assertThat(num(again.body(), "grossProfit")).isEqualTo(280.0);
    }

    @Test
    void multiLineSaleWithDiscountTaxAndPayments() {
        Session admin = registerBusiness("Multi Co");
        long a = defaultVariant(post(admin, "/api/products", pricedProduct("Rice", 50, "40", "60")).body());
        long b = defaultVariant(post(admin, "/api/products", pricedProduct("Oil", 20, "100", "150")).body());

        // Rice: 10 x 60 = 600 - 50 discount = 550 net, 18% tax = 99 → 649. Oil: 2 x 150 = 300, no tax → 300.
        Map<String, Object> body = paid(sale(line(a, 10, "60", "50", "18"), line(b, 2, "150", "0", "0")), "UPI", "500");
        body.put("customerName", "Ravi");
        body.put("customerPhone", "9999999999");
        body.put("saveCustomer", true);
        Response s = post(admin, "/api/sales", body);
        assertThat(s.status()).as(s.toString()).isEqualTo(201);
        assertThat(num(s.body(), "subtotal")).isEqualTo(900.0);
        assertThat(num(s.body(), "discountTotal")).isEqualTo(50.0);
        assertThat(num(s.body(), "taxTotal")).isEqualTo(99.0);
        assertThat(num(s.body(), "total")).isEqualTo(949.0);
        assertThat(num(s.body(), "netSales")).isEqualTo(850.0);
        assertThat(num(s.body(), "costTotal")).isEqualTo(600.0);
        assertThat(num(s.body(), "grossProfit")).isEqualTo(250.0);
        assertThat(s.get("paymentStatus").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(num(s.body(), "outstanding")).isEqualTo(449.0);
        assertThat(s.get("customerId").isNumber()).isTrue();

        long id = s.get("id").asLong();
        Response tooMuch = post(admin, "/api/sales/" + id + "/payments", payment("500", "CASH"));
        assertThat(tooMuch.status()).isEqualTo(400);
        Response rest = post(admin, "/api/sales/" + id + "/payments", payment("449", "CASH"));
        assertThat(rest.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(num(rest.body(), "outstanding")).isZero();
        assertThat(rest.get("payments").size()).isEqualTo(2);

        Response byInvoice = get(admin, "/api/sales?q=" + s.get("invoiceNumber").asText());
        assertThat(byInvoice.get("totalElements").asInt()).isEqualTo(1);
        assertThat(get(admin, "/api/sales?q=ravi").get("totalElements").asInt()).isEqualTo(1);
        assertThat(get(admin, "/api/sales?paymentStatus=UNPAID").get("totalElements").asInt()).isZero();
        assertThat(get(admin, "/api/customers?q=ravi").body().size()).isEqualTo(1);

        Map<String, Object> badDiscount = sale(line(a, 1, "60", "61", "0"));
        assertThat(post(admin, "/api/sales", badDiscount).status()).isEqualTo(400);
    }

    @Test
    void insufficientStockRollsBackTheWholeSale() {
        Session admin = registerBusiness("Rollback Co");
        Response p1 = post(admin, "/api/products", product("Pen", 10));
        Response p2 = post(admin, "/api/products", product("Ink", 1));
        Response r = post(admin, "/api/sales", sale(line(defaultVariant(p1.body()), 4, "10"), line(defaultVariant(p2.body()), 5, "10")));
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(r.get("message").asText()).contains("Ink");
        assertThat(available(get(admin, "/api/products/" + p1.get("id").asLong()))).isEqualTo(10.0);
        assertThat(get(admin, "/api/sales").get("totalElements").asInt()).isZero();
    }

    @Test
    void repeatedSaleRequestIsRecordedOnce() {
        Session admin = registerBusiness("Retry Co");
        Response p = post(admin, "/api/products", product("Mug", 10));
        Map<String, Object> body = sale(line(defaultVariant(p.body()), 2, "50"));
        Response first = post(admin, "/api/sales", body);
        Response second = post(admin, "/api/sales", body);
        assertThat(first.status()).isEqualTo(201);
        assertThat(second.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(available(get(admin, "/api/products/" + p.get("id").asLong()))).isEqualTo(8.0);
    }

    @Test
    void cancellationRestoresStockOnceAndRefunds() {
        Session admin = registerBusiness("Cancel Co");
        Response p = post(admin, "/api/products", product("Kettle", 10));
        long productId = p.get("id").asLong();
        Response sold = post(admin, "/api/sales", paid(sale(line(defaultVariant(p.body()), 3, "250.50")), "CASH", "751.50"));
        long saleId = sold.get("id").asLong();

        Response cancelled = post(admin, "/api/sales/" + saleId + "/cancel", Map.of("reason", "Entered by mistake"));
        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(num(cancelled.body(), "amountPaid")).isZero();
        assertThat(cancelled.get("payments").get(1).get("kind").asText()).isEqualTo("REFUND");
        assertThat(available(get(admin, "/api/products/" + productId))).isEqualTo(10.0);

        assertThat(post(admin, "/api/sales/" + saleId + "/cancel", Map.of("reason", "again")).status()).isEqualTo(409);
        assertThat(post(admin, "/api/sales/" + saleId + "/returns",
                returnOf(sold.get("items").get(0).get("id").asLong(), 1, true, null, "x")).status()).isEqualTo(409);
        assertThat(available(get(admin, "/api/products/" + productId))).isEqualTo(10.0);
    }

    @Test
    void returnsRestockResalableItemsAndAdjustProfit() {
        Session admin = registerBusiness("Return Co");
        Response p = post(admin, "/api/products", pricedProduct("Shirt", 20, "80", "120"));
        long productId = p.get("id").asLong();
        Response sold = post(admin, "/api/sales", paid(sale(line(defaultVariant(p.body()), 10, "120")), "CASH", "1200"));
        long saleId = sold.get("id").asLong();
        long itemId = sold.get("items").get(0).get("id").asLong();
        assertThat(available(get(admin, "/api/products/" + productId))).isEqualTo(10.0);

        Map<String, Object> resalable = returnOf(itemId, 3, true, "360", "Wrong size");
        Response r1 = post(admin, "/api/sales/" + saleId + "/returns", resalable);
        assertThat(r1.status()).as(r1.toString()).isEqualTo(200);
        assertThat(post(admin, "/api/sales/" + saleId + "/returns", resalable).get("returns").size()).isEqualTo(1);
        assertThat(available(get(admin, "/api/products/" + productId))).isEqualTo(13.0);

        Response r2 = post(admin, "/api/sales/" + saleId + "/returns", returnOf(itemId, 2, false, "0", "Torn"));
        assertThat(available(get(admin, "/api/products/" + productId))).isEqualTo(13.0);

        // 5 units remain sold: net 600; cost = 800 - 3 x 80 restocked = 560 (damaged units stay a cost) → profit 40.
        assertThat(num(r2.body(), "netSales")).isEqualTo(600.0);
        assertThat(num(r2.body(), "grossProfit")).isEqualTo(40.0);
        assertThat(num(r2.body(), "returnedTotal")).isEqualTo(600.0);
        assertThat(num(r2.body(), "amountPaid")).isEqualTo(840.0);
        assertThat(num(r2.body(), "outstanding")).isZero();
        assertThat(r2.get("items").get(0).get("returnedQuantity").asDouble()).isEqualTo(5.0);

        Response over = post(admin, "/api/sales/" + saleId + "/returns", returnOf(itemId, 6, true, null, "More"));
        assertThat(over.status()).isEqualTo(400);
        Response tooMuchRefund = post(admin, "/api/sales/" + saleId + "/returns", returnOf(itemId, 1, true, "1000", "x"));
        assertThat(tooMuchRefund.status()).isEqualTo(400);
    }

    @Test
    void purchasesAddStockAndUseWeightedAverageCost() {
        Session admin = registerBusiness("Buy Co");
        Response p = post(admin, "/api/products", pricedProduct("Cable", 100, "80", "120"));
        long productId = p.get("id").asLong();
        long variant = defaultVariant(p.body());

        Response bought = post(admin, "/api/purchases", purchase(variant, 50, "110"));
        assertThat(bought.status()).as(bought.toString()).isEqualTo(201);
        assertThat(bought.get("purchaseNumber").asText()).startsWith("PUR-");
        assertThat(num(bought.body(), "total")).isEqualTo(5500.0);
        assertThat(bought.get("paymentStatus").asText()).isEqualTo("UNPAID");
        Response product = get(admin, "/api/products/" + productId);
        assertThat(available(product)).isEqualTo(150.0);
        assertThat(num(product.body(), "averageCost")).isEqualTo(90.0);
        assertThat(num(product.body(), "purchasePrice")).isEqualTo(110.0);
        assertThat(types(get(admin, "/api/stock/movements?productId=" + productId).body())).first().isEqualTo("PURCHASE");
        assertThat(get(admin, "/api/suppliers").body().size()).isEqualTo(1);

        Response sold = post(admin, "/api/sales", sale(line(variant, 10, "120")));
        assertThat(num(sold.get("items").get(0), "unitCost")).isEqualTo(90.0);
        assertThat(num(sold.body(), "grossProfit")).isEqualTo(300.0);

        Response paidPart = post(admin, "/api/purchases/" + bought.get("id").asLong() + "/payments", payment("2000", "BANK_TRANSFER"));
        assertThat(paidPart.get("paymentStatus").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(num(paidPart.body(), "outstanding")).isEqualTo(3500.0);

        Response cancelled = post(admin, "/api/purchases/" + bought.get("id").asLong() + "/cancel", Map.of("reason", "Wrong delivery"));
        assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
        Response after = get(admin, "/api/products/" + productId);
        assertThat(available(after)).isEqualTo(90.0);
        // 140 left at 90 = 12600; removing 50 at 110 leaves 7100 over 90 units.
        assertThat(num(after.body(), "averageCost")).isEqualTo(78.8889);
    }

    @Test
    void rolesAreEnforcedOnTheServer() {
        Session admin = registerBusiness("Roles Co");
        Session staff = addEmployee(admin, "STAFF");
        Response p = post(admin, "/api/products", pricedProduct("Cup", 10, "5", "20"));
        long variant = defaultVariant(p.body());

        Response sold = post(staff, "/api/sales", sale(line(variant, 1, "20")));
        assertThat(sold.status()).isEqualTo(201);
        assertThat(sold.body().has("grossProfit")).isFalse();
        assertThat(sold.get("items").get(0).has("unitCost")).isFalse();
        long saleId = sold.get("id").asLong();
        assertThat(post(staff, "/api/sales/" + saleId + "/cancel", Map.of("reason", "Oops")).status()).isEqualTo(403);
        assertThat(post(staff, "/api/sales/" + saleId + "/returns",
                returnOf(sold.get("items").get(0).get("id").asLong(), 1, true, null, "x")).status()).isEqualTo(403);
        assertThat(post(staff, "/api/purchases", purchase(variant, 1, "5")).status()).isEqualTo(403);
        assertThat(get(staff, "/api/analytics/overview").status()).isEqualTo(403);
        assertThat(post(staff, "/api/sales/" + saleId + "/payments", payment("20", "CASH")).status()).isEqualTo(200);
    }

    @Test
    void salesPurchasesAndCustomersAreIsolatedBetweenBusinesses() {
        Session a = registerBusiness("Tenant A");
        Session b = registerBusiness("Tenant B");
        long variant = defaultVariant(post(a, "/api/products", product("Secret", 10)).body());
        Map<String, Object> s = sale(line(variant, 1, "10"));
        s.put("customerName", "Private Person");
        s.put("saveCustomer", true);
        long saleId = post(a, "/api/sales", s).get("id").asLong();
        long purchaseId = post(a, "/api/purchases", purchase(variant, 1, "5")).get("id").asLong();

        assertThat(get(b, "/api/sales/" + saleId).status()).isEqualTo(404);
        assertThat(post(b, "/api/sales/" + saleId + "/cancel", Map.of("reason", "x")).status()).isEqualTo(404);
        assertThat(post(b, "/api/sales", sale(line(variant, 1, "10"))).status()).isEqualTo(404);
        assertThat(get(b, "/api/purchases/" + purchaseId).status()).isEqualTo(404);
        assertThat(get(b, "/api/sales").get("totalElements").asInt()).isZero();
        assertThat(get(b, "/api/customers").body().size()).isZero();
        assertThat(get(b, "/api/suppliers").body().size()).isZero();
        assertThat(get(b, "/api/analytics/overview").get("units").get("value").asDouble()).isZero();
    }

    @Test
    void concurrentSalesNeverOversell() throws Exception {
        Session admin = registerBusiness("Rush Co");
        Response p = post(admin, "/api/products", product("Ticket", 10));
        long variant = defaultVariant(p.body());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            results.add(pool.submit(() -> post(admin, "/api/sales", sale(line(variant, 1, "5"))).status()));
        }
        int ok = 0;
        for (Future<Integer> f : results) if (f.get(60, TimeUnit.SECONDS) == 201) ok++;
        pool.shutdown();
        assertThat(ok).isEqualTo(10);
        assertThat(available(get(admin, "/api/products/" + p.get("id").asLong()))).isZero();
        assertThat(get(admin, "/api/sales").get("totalElements").asInt()).isEqualTo(10);
    }

    @Test
    void stockOperationWithSameRequestIdIsAppliedOnce() {
        Session admin = registerBusiness("Idem Co");
        Response p = post(admin, "/api/products", product("Nail", 10));
        Map<String, Object> body = new HashMap<>(op("REDUCE", defaultVariant(p.body()), 4));
        body.put("clientRequestId", UUID.randomUUID().toString());
        Response first = post(admin, "/api/stock/operations", body);
        Response second = post(admin, "/api/stock/operations", body);
        assertThat(second.get("movements").get(0).get("id").asLong()).isEqualTo(first.get("movements").get(0).get("id").asLong());
        assertThat(available(get(admin, "/api/products/" + p.get("id").asLong()))).isEqualTo(6.0);
    }

    @Test
    void saleDateCannotBeInTheFuture() {
        Session admin = registerBusiness("Future Co");
        long variant = defaultVariant(post(admin, "/api/products", product("Pen", 10)).body());
        Response r = post(admin, "/api/sales", sale(Instant.now().plus(2, ChronoUnit.DAYS), List.of(line(variant, 1, "5"))));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.get("fieldErrors").has("soldAt")).isTrue();
    }
}
