package com.b2binventory;

import com.b2binventory.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.b2binventory.support.Fixtures.*;
import static com.b2binventory.support.SalesFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsTest extends IntegrationTest {

    private static final String ZONE = "Asia/Kolkata";

    private Session business(String name) {
        Session admin = registerBusiness(name);
        put(admin, "/api/business/settings", Map.of("allowNegativeStock", false, "defaultLowStockThreshold", 5,
                "notifyLowStock", true, "timezone", ZONE, "currencyCode", "INR"));
        return admin;
    }

    private long newProduct(Session s, String name, int stock, String cost, String price) {
        return defaultVariant(post(s, "/api/products", pricedProduct(name, stock, cost, price)).body());
    }

    private Response sell(Session s, long variant, int qty, int price, LocalDate day) {
        var at = day.equals(today()) ? java.time.Instant.now() : day.atTime(LocalTime.NOON).atZone(ZoneId.of(ZONE)).toInstant();
        Response r = post(s, "/api/sales", sale(at, List.of(line(variant, qty, price))));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of(ZONE));
    }

    private static List<String> names(JsonNode products) {
        List<String> out = new ArrayList<>();
        products.forEach(p -> out.add(p.get("name").asText()));
        return out;
    }

    private static JsonNode find(JsonNode products, String name) {
        for (JsonNode p : products) if (p.get("name").asText().equals(name)) return p;
        throw new AssertionError("missing " + name);
    }

    @Test
    void ranksProductsAndSeparatesRevenueFromProfit() {
        Session s = business("Rank Co");
        long apple = newProduct(s, "Apple", 100, "3", "5");
        long banana = newProduct(s, "Banana", 100, "12", "20");
        long cherry = newProduct(s, "Cherry", 100, "2", "3");
        newProduct(s, "Durian", 100, "50", "80");
        LocalDate today = today();
        sell(s, apple, 10, 5, today);
        sell(s, banana, 4, 20, today);
        sell(s, cherry, 1, 3, today);
        long cancelled = sell(s, cherry, 50, 3, today).get("id").asLong();
        post(s, "/api/sales/" + cancelled + "/cancel", Map.of("reason", "Entered by mistake"));

        String range = "?from=" + today.minusDays(6) + "&to=" + today;
        Response overview = get(s, "/api/analytics/overview" + range);
        assertThat(overview.status()).isEqualTo(200);
        assertThat(overview.get("units").get("value").asDouble()).isEqualTo(15.0);
        assertThat(overview.get("revenue").get("value").asDouble()).isEqualTo(133.0);
        assertThat(overview.get("sales").get("value").asInt()).isEqualTo(3);
        // Cost: 10x3 + 4x12 + 1x2 = 80 → gross profit 53.
        JsonNode fin = overview.get("financials");
        assertThat(fin.get("costOfGoodsSold").asDouble()).isEqualTo(80.0);
        assertThat(fin.get("grossProfit").asDouble()).isEqualTo(53.0);
        assertThat(fin.get("profitAvailable").asBoolean()).isTrue();
        assertThat(overview.get("grossProfit").get("value").asDouble()).isEqualTo(53.0);
        assertThat(overview.get("bestSeller").get("name").asText()).isEqualTo("Apple");
        assertThat(overview.get("lowestSeller").get("name").asText()).isEqualTo("Cherry");
        assertThat(overview.get("productsWithNoSales").asInt()).isEqualTo(1);
        assertThat(overview.get("units").hasNonNull("growthPercent")).isFalse();
        // Inventory left: Apple 90x3 + Banana 96x12 + Cherry 99x2 + Durian 100x50 = 6620 at cost.
        assertThat(overview.get("inventory").get("valueAtCost").asDouble()).isEqualTo(6620.0);

        JsonNode products = get(s, "/api/analytics/products" + range).get("products");
        assertThat(names(products)).containsExactly("Apple", "Banana", "Cherry", "Durian");
        assertThat(find(products, "Apple").get("labels").toString()).contains("TOP_SELLER");
        assertThat(find(products, "Durian").get("labels").toString()).contains("NO_SALES");
        assertThat(find(products, "Banana").get("grossProfit").asDouble()).isEqualTo(32.0);
        assertThat(find(products, "Cherry").get("currentStock").asDouble()).isEqualTo(99.0);
        assertThat(get(s, "/api/analytics/products" + range + "&sort=revenue").get("products").get(0).get("name").asText())
                .isEqualTo("Banana");
        assertThat(get(s, "/api/analytics/products" + range + "&sort=profit").get("products").get(0).get("name").asText())
                .isEqualTo("Banana");

        double trendUnits = 0;
        for (JsonNode point : get(s, "/api/analytics/trend" + range).get("points")) trendUnits += point.get("units").asDouble();
        assertThat(trendUnits).isEqualTo(15.0);
        assertThat(get(s, "/api/analytics/categories" + range).get("categories").get(0).get("units").asDouble()).isEqualTo(15.0);
    }

    @Test
    void stockReductionsAreNotSales() {
        Session s = business("Reduce Co");
        long item = newProduct(s, "Item", 50, "1", "2");
        post(s, "/api/stock/operations", op("REDUCE", item, 10));
        Response overview = get(s, "/api/analytics/overview");
        assertThat(overview.get("units").get("value").asDouble()).isZero();
        assertThat(overview.get("revenue").get("value").asDouble()).isZero();
    }

    @Test
    void rankingFollowsTheSelectedPeriodAndComparesWithThePreviousOne() {
        Session s = business("Period Co");
        long old = newProduct(s, "Oldie", 100, "5", "10");
        long fresh = newProduct(s, "Fresh", 100, "5", "10");
        LocalDate today = today();
        sell(s, old, 2, 10, today.minusDays(40));
        sell(s, old, 8, 10, today.minusDays(10));
        sell(s, fresh, 3, 10, today);

        assertThat(get(s, "/api/analytics/overview?from=" + today.minusDays(2) + "&to=" + today)
                .get("bestSeller").get("name").asText()).isEqualTo("Fresh");
        String month = "?from=" + today.minusDays(29) + "&to=" + today;
        Response overview = get(s, "/api/analytics/overview" + month);
        assertThat(overview.get("bestSeller").get("name").asText()).isEqualTo("Oldie");
        // Profit 55 now vs 10 before.
        assertThat(overview.get("grossProfit").get("growthPercent").asDouble()).isEqualTo(450.0);

        JsonNode oldie = find(get(s, "/api/analytics/products" + month).get("products"), "Oldie");
        assertThat(oldie.get("previousUnits").asDouble()).isEqualTo(2.0);
        assertThat(oldie.get("growthPercent").asDouble()).isEqualTo(300.0);
        assertThat(oldie.get("performance").asText()).isEqualTo("GROWING");
        assertThat(find(get(s, "/api/analytics/products" + month).get("products"), "Fresh").get("performance").asText())
                .isEqualTo("NEW");
        assertThat(get(s, "/api/analytics/insights" + month).get("insights").toString()).contains("PROFIT_TREND");
    }

    @Test
    void returnsReduceRevenueAndUnits() {
        Session s = business("Returns Co");
        long item = newProduct(s, "Jacket", 20, "60", "100");
        Response sold = sell(s, item, 5, 100, today());
        post(s, "/api/sales/" + sold.get("id").asLong() + "/returns",
                returnOf(sold.get("items").get(0).get("id").asLong(), 2, true, null, "Did not fit"));
        Response overview = get(s, "/api/analytics/overview");
        JsonNode fin = overview.get("financials");
        assertThat(fin.get("grossSales").asDouble()).isEqualTo(500.0);
        assertThat(fin.get("returns").asDouble()).isEqualTo(200.0);
        assertThat(fin.get("netSales").asDouble()).isEqualTo(300.0);
        assertThat(fin.get("costOfGoodsSold").asDouble()).isEqualTo(180.0);
        assertThat(fin.get("grossProfit").asDouble()).isEqualTo(120.0);
        assertThat(overview.get("units").get("value").asDouble()).isEqualTo(3.0);
    }

    @Test
    void profitIsUnavailableWhenCostIsMissing() {
        Session s = business("NoCost Co");
        long item = defaultVariant(post(s, "/api/products", product("Mystery", 10)).body());
        sell(s, item, 2, 50, today());
        Response overview = get(s, "/api/analytics/overview");
        assertThat(overview.get("revenue").get("value").asDouble()).isEqualTo(100.0);
        assertThat(overview.body().hasNonNull("grossProfit")).isFalse();
        assertThat(overview.get("financials").get("profitAvailable").asBoolean()).isFalse();
        assertThat(overview.get("financials").hasNonNull("grossProfit")).isFalse();
    }

    @Test
    void trendBucketsUseTheBusinessTimezoneAndFillGaps() {
        Session s = business("Trend Co");
        long item = newProduct(s, "Item", 100, "1", "10");
        LocalDate today = today();
        sell(s, item, 2, 10, today.minusDays(2));
        sell(s, item, 5, 10, today);
        JsonNode points = get(s, "/api/analytics/trend?from=" + today.minusDays(2) + "&to=" + today + "&granularity=day")
                .get("points");
        assertThat(points.size()).isEqualTo(3);
        assertThat(points.get(0).get("start").asText()).isEqualTo(today.minusDays(2).toString());
        assertThat(points.get(0).get("units").asDouble()).isEqualTo(2.0);
        assertThat(points.get(1).get("units").asDouble()).isZero();
        assertThat(points.get(2).get("units").asDouble()).isEqualTo(5.0);
        assertThat(points.get(2).get("grossProfit").asDouble()).isEqualTo(45.0);
    }

    @Test
    void insightsAndDashboardReflectRealSales() {
        Session s = business("Insight Co");
        long hot = newProduct(s, "Hot item", 6, "60", "100");
        newProduct(s, "Idle item", 10, "5", "8");
        sell(s, hot, 5, 100, today());

        String text = get(s, "/api/analytics/insights?from=" + today().minusDays(6) + "&to=" + today()).get("insights").toString();
        assertThat(text).contains("You sold 5 pcs of Hot item");
        assertThat(text).contains("Idle item had no recorded sales");
        assertThat(text).contains("REPLENISH");

        JsonNode sales = get(s, "/api/dashboard").get("sales");
        assertThat(sales.get("today").get("units").asDouble()).isEqualTo(5.0);
        assertThat(sales.get("today").get("revenue").asDouble()).isEqualTo(500.0);
        assertThat(sales.get("today").get("grossProfit").asDouble()).isEqualTo(200.0);
        assertThat(sales.get("month").get("revenue").asDouble()).isEqualTo(500.0);
        assertThat(sales.get("monthBestSeller").get("name").asText()).isEqualTo("Hot item");
        assertThat(sales.get("inventory").get("valueAtCost").asDouble()).isEqualTo(110.0);

        Session staff = addEmployee(s, "STAFF");
        assertThat(get(staff, "/api/dashboard").body().hasNonNull("sales")).isFalse();
    }

    @Test
    void emptyPeriodHasNoFakeFigures() {
        Session s = business("Empty Co");
        newProduct(s, "Nothing sold", 5, "1", "2");
        Response overview = get(s, "/api/analytics/overview");
        assertThat(overview.get("units").get("value").asDouble()).isZero();
        assertThat(overview.body().hasNonNull("bestSeller")).isFalse();
        assertThat(overview.get("revenue").hasNonNull("growthPercent")).isFalse();
        assertThat(get(s, "/api/analytics/insights").get("insights").get(0).get("kind").asText()).isEqualTo("NO_SALES_PERIOD");
        assertThat(get(s, "/api/analytics/overview?from=2026-02-10&to=2026-02-01").status()).isEqualTo(400);
    }

    @Test
    void exportsProductAnalytics() throws Exception {
        Session s = business("Export Co");
        sell(s, newProduct(s, "Widget", 10, "5", "15"), 2, 15, today());
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/analytics/products/export?format=csv")
                        .header("Authorization", "Bearer " + s.token()))
                .andReturn().getResponse();
        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(result.getContentAsString()).contains("Widget").contains("Gross profit");
    }
}
