package com.b2binventory;

import com.b2binventory.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static com.b2binventory.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business network and trade invoices: Deepak Traders (wholesaler) invoices Shri Ji Sales (retailer); the retailer
 * approves and both inventories, the seller's sale and the buyer's purchase are posted together.
 */
class TradeInvoiceTest extends IntegrationTest {

    // ------------------------------------------------------------------ helpers

    private void connect(Session a, Session b) {
        Response req = post(a, "/api/network/connections", Map.of("businessId", b.businessId()));
        assertThat(req.status()).isEqualTo(201);
        assertThat(post(b, "/api/network/connections/" + req.get("id").asLong() + "/accept", null).status()).isEqualTo(200);
    }

    /** The business's unit with this code (new businesses already have common units such as PCS). */
    private long unit(Session s, String code, String name) {
        for (JsonNode u : get(s, "/api/units").body()) {
            if (u.get("code").asText().equalsIgnoreCase(code)) return u.get("id").asLong();
        }
        Response r = post(s, "/api/units", Map.of("code", code, "name", name, "decimals", 0));
        assertThat(r.status()).isEqualTo(201);
        return r.get("id").asLong();
    }

    /** Creates a product with one size variant ("8x4 ft · 1 mm") and returns {productId, variantId}. */
    private long[] micaSheet(Session s, long unitId, int qty) {
        Response p = post(s, "/api/products", Map.of("name", "Mica Sheet", "baseUnitId", unitId, "brand", "Royal",
                "salePrice", 500));
        assertThat(p.status()).isEqualTo(201);
        long productId = p.get("id").asLong();
        Response withVariant = post(s, "/api/products/" + productId + "/variants", Map.of("name", "8x4 ft · 1 mm",
                "attributes", Map.of("Size", "8 x 4 feet", "Thickness", "1 mm")));
        assertThat(withVariant.status()).isIn(200, 201);
        long variantId = 0;
        for (JsonNode v : withVariant.get("variants")) if (v.get("name").asText().equals("8x4 ft · 1 mm")) variantId = v.get("id").asLong();
        assertThat(post(s, "/api/stock/operations", op("ADD", variantId, qty)).status()).isEqualTo(200);
        return new long[]{productId, variantId};
    }

    /** Totals across warehouses for one variant: [onHand, reserved, available]. */
    private double[] stockOf(Session s, long variantId) {
        Response r = get(s, "/api/variants/" + variantId + "/balances");
        assertThat(r.status()).isEqualTo(200);
        double onHand = 0, reserved = 0, available = 0;
        for (JsonNode b : r.body()) {
            onHand += b.get("onHand").asDouble();
            reserved += b.get("reserved").asDouble();
            available += b.get("available").asDouble();
        }
        return new double[]{onHand, reserved, available};
    }

    private Response invoice(Session seller, Session buyer, long variantId, int qty, double price, boolean send) {
        return post(seller, "/api/trade/invoices", Map.of("buyerBusinessId", buyer.businessId(), "send", send,
                "notes", "Deliver by Monday", "lines", List.of(Map.of("variantId", variantId, "quantity", qty, "unitPrice", price))));
    }

    private int movementCount(Session s, long variantId) {
        return get(s, "/api/stock/movements?variantId=" + variantId + "&size=200").get("totalElements").asInt();
    }

    // ------------------------------------------------------------------ the main flow

    @Test
    void approvedInvoiceMovesExactStockBetweenBusinessesOnce() {
        Session deepak = registerBusiness("Deepak Traders");
        Session shriJi = registerBusiness("Shri Ji Sales");
        Session gupta = registerBusiness("Gupta Enterprises");
        connect(deepak, shriJi);

        long[] seller = micaSheet(deepak, unit(deepak, "SHT", "Sheet"), 100);
        // The buyer already stocks the same sheet in the same size and unit (10 sheets).
        long[] existing = micaSheet(shriJi, unit(shriJi, "SHT", "Sheet"), 10);

        Response sent = invoice(deepak, shriJi, seller[1], 25, 450, true);
        assertThat(sent.status()).isEqualTo(201);
        long id = sent.get("id").asLong();
        assertThat(sent.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(sent.get("invoiceNumber").asText()).startsWith("DT-");
        assertThat(sent.get("total").asDouble()).isEqualTo(11250.0);
        JsonNode line = sent.get("lines").get(0);
        assertThat(line.get("variantName").asText()).isEqualTo("8x4 ft · 1 mm");
        assertThat(line.get("unitCode").asText()).isEqualTo("SHT");
        assertThat(line.get("variantAttributes").get("Thickness").asText()).isEqualTo("1 mm");

        // Sending holds the seller's stock: on hand unchanged, 25 reserved.
        assertThat(stockOf(deepak, seller[1])).containsExactly(100, 25, 75);
        // Nothing reaches the buyer's stock until approval.
        assertThat(stockOf(shriJi, existing[1])).containsExactly(10, 0, 10);

        // Only the buyer is notified, and the notification opens this invoice.
        JsonNode notes = get(shriJi, "/api/notifications").get("content");
        assertThat(notes.get(0).get("type").asText()).isEqualTo("TRADE_INVOICE_RECEIVED");
        assertThat(notes.get(0).get("payload").get("tradeInvoiceId").asLong()).isEqualTo(id);
        get(gupta, "/api/notifications").get("content").forEach(n ->
                assertThat(n.get("type").asText()).isNotEqualTo("TRADE_INVOICE_RECEIVED"));

        // Other businesses cannot see or act on it; the seller cannot approve its own invoice.
        assertThat(get(gupta, "/api/trade/invoices/" + id).status()).isEqualTo(404);
        assertThat(post(gupta, "/api/trade/invoices/" + id + "/approve", Map.of()).status()).isEqualTo(404);
        assertThat(post(deepak, "/api/trade/invoices/" + id + "/approve", Map.of()).status()).isEqualTo(404);

        // The preview proposes the buyer's existing sheet (same name, size and unit).
        Response preview = get(shriJi, "/api/trade/invoices/" + id + "/preview");
        assertThat(preview.get("lines").get(0).get("mode").asText()).isEqualTo("MATCHED");
        assertThat(preview.get("lines").get(0).get("target").get("variantId").asLong()).isEqualTo(existing[1]);

        Response approved = post(shriJi, "/api/trade/invoices/" + id + "/approve", Map.of());
        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.get("status").asText()).isEqualTo("POSTED");

        // Before: seller 100, buyer 10. After 25 sheets: seller 75, buyer 35.
        assertThat(stockOf(deepak, seller[1])).containsExactly(75, 0, 75);
        assertThat(stockOf(shriJi, existing[1])).containsExactly(35, 0, 35);

        // The buyer's purchase carries the agreed price, linked to the seller as a supplier.
        long purchaseId = approved.get("purchaseId").asLong();
        Response purchase = get(shriJi, "/api/purchases/" + purchaseId);
        assertThat(purchase.get("referenceNumber").asText()).isEqualTo(sent.get("invoiceNumber").asText());
        assertThat(purchase.get("supplierName").asText()).isEqualTo("Deepak Traders");
        assertThat(purchase.get("total").asDouble()).isEqualTo(11250.0);
        assertThat(purchase.get("items").get(0).get("unitCost").asDouble()).isEqualTo(450.0);
        // The seller's sale uses the invoice number and the buyer as customer.
        Response sale = get(deepak, "/api/sales/" + get(deepak, "/api/trade/invoices/" + id).get("saleId").asLong());
        assertThat(sale.get("invoiceNumber").asText()).isEqualTo(sent.get("invoiceNumber").asText());
        assertThat(sale.get("customerName").asText()).isEqualTo("Shri Ji Sales");
        assertThat(sale.get("total").asDouble()).isEqualTo(11250.0);
        // The buyer never sees the seller's sale id, nor the seller the buyer's purchase id.
        assertThat(approved.body().path("saleId").isMissingNode() || approved.body().get("saleId").isNull()).isTrue();

        // Approving again (double tap, retry) changes nothing.
        int sellerMoves = movementCount(deepak, seller[1]);
        int buyerMoves = movementCount(shriJi, existing[1]);
        assertThat(post(shriJi, "/api/trade/invoices/" + id + "/approve", Map.of()).get("status").asText()).isEqualTo("POSTED");
        assertThat(movementCount(deepak, seller[1])).isEqualTo(sellerMoves);
        assertThat(movementCount(shriJi, existing[1])).isEqualTo(buyerMoves);
        assertThat(stockOf(shriJi, existing[1])).containsExactly(35, 0, 35);
        assertThat(get(shriJi, "/api/purchases").get("totalElements").asInt()).isEqualTo(1);

        // A posted invoice cannot be cancelled or rejected any more.
        assertThat(post(deepak, "/api/trade/invoices/" + id + "/cancel", Map.of("reason", "x")).status()).isEqualTo(409);
        assertThat(post(shriJi, "/api/trade/invoices/" + id + "/reject", Map.of("reason", "x")).status()).isEqualTo(409);

        // The seller is told, and both sides list the invoice.
        assertThat(get(deepak, "/api/notifications").get("content").get(0).get("type").asText()).isEqualTo("TRADE_INVOICE_APPROVED");
        assertThat(get(deepak, "/api/trade/invoices?direction=SALES").get("totalElements").asInt()).isEqualTo(1);
        assertThat(get(shriJi, "/api/trade/invoices?direction=PURCHASES").get("totalElements").asInt()).isEqualTo(1);
        assertThat(get(gupta, "/api/trade/invoices?direction=PURCHASES").get("totalElements").asInt()).isZero();
        List<String> actions = new ArrayList<>();
        approved.get("events").forEach(e -> actions.add(e.get("action").asText()));
        assertThat(actions).containsExactly("CREATED", "SENT", "APPROVED");
    }

    @Test
    void unmatchedProductIsCreatedForTheBuyerWithTheSameSizeUnitAndPrice() {
        Session seller = registerBusiness("Sharma Traders");
        Session buyer = registerBusiness("New Retail Co");
        connect(buyer, seller);
        long[] sheet = micaSheet(seller, unit(seller, "SHT", "Sheet"), 40);

        long id = invoice(seller, buyer, sheet[1], 25, 450, true).get("id").asLong();
        assertThat(get(buyer, "/api/trade/invoices/" + id + "/preview").get("lines").get(0).get("mode").asText()).isEqualTo("NEW");
        Response approved = post(buyer, "/api/trade/invoices/" + id + "/approve", Map.of());
        assertThat(approved.status()).isEqualTo(200);

        long buyerVariant = approved.get("lines").get(0).get("buyerVariantId").asLong();
        assertThat(stockOf(buyer, buyerVariant)).containsExactly(25, 0, 25);
        Response products = get(buyer, "/api/products?q=Mica");
        assertThat(products.get("totalElements").asInt()).isEqualTo(1);
        Response created = get(buyer, "/api/products/" + products.get("content").get(0).get("id").asLong());
        assertThat(created.get("baseUnit").get("code").asText()).isEqualTo("SHT");
        assertThat(created.get("brand").asText()).isEqualTo("Royal");
        assertThat(created.get("purchasePrice").asDouble()).isEqualTo(450.0);
        JsonNode variant = null;
        for (JsonNode v : created.get("variants")) if (v.get("id").asLong() == buyerVariant) variant = v;
        assertThat(variant).isNotNull();
        assertThat(variant.get("name").asText()).isEqualTo("8x4 ft · 1 mm");
        assertThat(variant.get("attributes").get("Size").asText()).isEqualTo("8 x 4 feet");
        assertThat(variant.get("attributes").get("Thickness").asText()).isEqualTo("1 mm");

        // The link is remembered: the next invoice for the same sheet goes into the same product.
        long second = invoice(seller, buyer, sheet[1], 5, 460, true).get("id").asLong();
        Response next = get(buyer, "/api/trade/invoices/" + second + "/preview");
        assertThat(next.get("lines").get(0).get("mode").asText()).isEqualTo("MAPPED");
        assertThat(post(buyer, "/api/trade/invoices/" + second + "/approve", Map.of()).status()).isEqualTo(200);
        assertThat(stockOf(buyer, buyerVariant)).containsExactly(30, 0, 30);
        assertThat(get(buyer, "/api/products?q=Mica").get("totalElements").asInt()).isEqualTo(1);
        assertThat(get(buyer, "/api/trade/mappings").body()).hasSize(1);

        // Each purchase keeps its own historical price.
        List<Double> costs = new ArrayList<>();
        for (JsonNode p : get(buyer, "/api/purchases").get("content")) {
            costs.add(get(buyer, "/api/purchases/" + p.get("id").asLong()).get("items").get(0).get("unitCost").asDouble());
        }
        assertThat(costs).containsExactlyInAnyOrder(450.0, 460.0);
    }

    @Test
    void ambiguousMatchMustBeChosenAndUnitsAreNeverConverted() {
        Session seller = registerBusiness("Balaji Wholesale");
        Session buyer = registerBusiness("Ganesh Store");
        connect(seller, buyer);
        long pcs = unit(seller, "PCS", "Piece");
        long variant = defaultVariant(post(seller, "/api/products", Map.of("name", "Door Handle", "baseUnitId", pcs,
                "openingStock", Map.of("quantity", 50))).body());
        // The buyer has two products both called "Door Handle", and one counted in boxes.
        long buyerPcs = unit(buyer, "PCS", "Piece");
        long a = defaultVariant(post(buyer, "/api/products", Map.of("name", "Door Handle", "baseUnitId", buyerPcs)).body());
        long b = defaultVariant(post(buyer, "/api/products", Map.of("name", "door handle", "baseUnitId", buyerPcs)).body());
        long box = defaultVariant(post(buyer, "/api/products", Map.of("name", "Handle box",
                "baseUnitId", unit(buyer, "BOX", "Box"))).body());

        long id = invoice(seller, buyer, variant, 10, 120, true).get("id").asLong();
        JsonNode proposal = get(buyer, "/api/trade/invoices/" + id + "/preview").get("lines").get(0);
        assertThat(proposal.get("mode").asText()).isEqualTo("REVIEW");
        assertThat(proposal.get("candidates")).hasSize(2);

        Response unresolved = post(buyer, "/api/trade/invoices/" + id + "/approve", Map.of());
        assertThat(unresolved.status()).isEqualTo(409);
        assertThat(unresolved.get("code").asText()).isEqualTo("NEEDS_REVIEW");
        assertThat(stockOf(buyer, a)[0]).isZero();

        long lineId = proposal.get("lineId").asLong();
        Response wrongUnit = post(buyer, "/api/trade/invoices/" + id + "/approve",
                Map.of("lines", List.of(Map.of("lineId", lineId, "variantId", box))));
        assertThat(wrongUnit.status()).isEqualTo(400);
        assertThat(stockOf(buyer, box)[0]).isZero();

        Response ok = post(buyer, "/api/trade/invoices/" + id + "/approve",
                Map.of("lines", List.of(Map.of("lineId", lineId, "variantId", b))));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(stockOf(buyer, b)).containsExactly(10, 0, 10);
        assertThat(stockOf(buyer, a)[0]).isZero();
    }

    @Test
    void rejectAndCancelReleaseHeldStockAndNeverReachTheBuyer() {
        Session seller = registerBusiness("Seller One");
        Session buyer = registerBusiness("Buyer One");
        connect(seller, buyer);
        long variant = defaultVariant(post(seller, "/api/products", product("Paint 20L", 30)).body());

        long rejected = invoice(seller, buyer, variant, 12, 900, true).get("id").asLong();
        assertThat(stockOf(seller, variant)).containsExactly(30, 12, 18);
        assertThat(post(buyer, "/api/trade/invoices/" + rejected + "/reject", Map.of()).status()).isEqualTo(400);
        Response r = post(buyer, "/api/trade/invoices/" + rejected + "/reject", Map.of("reason", "Wrong rate"));
        assertThat(r.get("status").asText()).isEqualTo("REJECTED");
        assertThat(r.get("rejectionReason").asText()).isEqualTo("Wrong rate");
        assertThat(stockOf(seller, variant)).containsExactly(30, 0, 30);
        assertThat(get(buyer, "/api/products").get("totalElements").asInt()).isZero();
        assertThat(get(buyer, "/api/purchases").get("totalElements").asInt()).isZero();
        assertThat(post(buyer, "/api/trade/invoices/" + rejected + "/approve", Map.of()).status()).isEqualTo(409);
        assertThat(get(seller, "/api/notifications").get("content").get(0).get("type").asText()).isEqualTo("TRADE_INVOICE_REJECTED");

        long cancelled = invoice(seller, buyer, variant, 5, 900, true).get("id").asLong();
        assertThat(stockOf(seller, variant)).containsExactly(30, 5, 25);
        assertThat(post(seller, "/api/trade/invoices/" + cancelled + "/cancel", Map.of("reason", "Customer called")).get("status")
                .asText()).isEqualTo("CANCELLED");
        assertThat(stockOf(seller, variant)).containsExactly(30, 0, 30);
        assertThat(post(buyer, "/api/trade/invoices/" + cancelled + "/approve", Map.of()).status()).isEqualTo(409);

        // Drafts hold no stock, are invisible to the buyer and can be deleted.
        long draft = invoice(seller, buyer, variant, 3, 900, false).get("id").asLong();
        assertThat(stockOf(seller, variant)).containsExactly(30, 0, 30);
        assertThat(get(buyer, "/api/trade/invoices/" + draft).status()).isEqualTo(404);
        assertThat(delete(seller, "/api/trade/invoices/" + draft).status()).isEqualTo(204);
    }

    @Test
    void invoicesNeedAConnectionStockAndValidLines() {
        Session seller = registerBusiness("Lone Seller");
        Session stranger = registerBusiness("Stranger Mart");
        long variant = defaultVariant(post(seller, "/api/products", product("Cement", 10)).body());

        Response notConnected = invoice(seller, stranger, variant, 1, 300, true);
        assertThat(notConnected.status()).isEqualTo(409);

        connect(seller, stranger);
        assertThat(invoice(seller, stranger, variant, 11, 300, true).status()).isEqualTo(422);
        assertThat(stockOf(seller, variant)).containsExactly(10, 0, 10);
        Response half = invoice(seller, stranger, variant, 0, 300, true);
        assertThat(half.status()).isEqualTo(400);
        Response twice = post(seller, "/api/trade/invoices", Map.of("buyerBusinessId", stranger.businessId(), "send", true,
                "lines", List.of(Map.of("variantId", variant, "quantity", 1, "unitPrice", 1),
                        Map.of("variantId", variant, "quantity", 1, "unitPrice", 1))));
        assertThat(twice.status()).isEqualTo(400);
        // A buyer cannot invoice using the seller's product ids.
        assertThat(invoice(stranger, seller, variant, 1, 300, true).status()).isEqualTo(404);
    }

    @Test
    void concurrentInvoicesCannotHoldMoreStockThanExists() throws Exception {
        Session seller = registerBusiness("Busy Wholesaler");
        Session buyer = registerBusiness("Busy Retailer");
        connect(seller, buyer);
        long variant = defaultVariant(post(seller, "/api/products", product("Ply 18mm", 100)).body());

        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> invoice(seller, buyer, variant, 30, 1000, true).status()));
        }
        int sent = 0;
        for (Future<Integer> f : results) if (f.get(60, TimeUnit.SECONDS) == 201) sent++;
        pool.shutdown();
        assertThat(sent).isEqualTo(3);
        assertThat(stockOf(seller, variant)).containsExactly(100, 90, 10);
    }

    @Test
    void returnsMoveStockBackOnBothSides() {
        Session seller = registerBusiness("Return Seller");
        Session buyer = registerBusiness("Return Buyer");
        connect(seller, buyer);
        long variant = defaultVariant(post(seller, "/api/products", product("Tiles", 100)).body());
        long id = invoice(seller, buyer, variant, 25, 40, true).get("id").asLong();
        Response posted = post(buyer, "/api/trade/invoices/" + id + "/approve", Map.of());
        long buyerVariant = posted.get("lines").get(0).get("buyerVariantId").asLong();
        long lineId = posted.get("lines").get(0).get("id").asLong();
        assertThat(stockOf(seller, variant)).containsExactly(75, 0, 75);

        // More than was bought is refused.
        assertThat(post(buyer, "/api/trade/invoices/" + id + "/returns", Map.of("reason", "Broken",
                "lines", List.of(Map.of("invoiceLineId", lineId, "quantity", 26)))).status()).isEqualTo(400);

        Response requested = post(buyer, "/api/trade/invoices/" + id + "/returns", Map.of("reason", "Broken in transit",
                "lines", List.of(Map.of("invoiceLineId", lineId, "quantity", 5))));
        assertThat(requested.status()).isEqualTo(201);
        long returnId = requested.get("returns").get(0).get("id").asLong();
        assertThat(stockOf(buyer, buyerVariant)).containsExactly(25, 5, 20);

        Response accepted = post(seller, "/api/trade/returns/" + returnId + "/accept", Map.of());
        assertThat(accepted.status()).isEqualTo(200);
        assertThat(accepted.get("returns").get(0).get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(stockOf(seller, variant)).containsExactly(80, 0, 80);
        assertThat(stockOf(buyer, buyerVariant)).containsExactly(20, 0, 20);
        assertThat(accepted.get("lines").get(0).get("returnedQuantity").asDouble()).isEqualTo(5.0);
        // Accepting twice changes nothing.
        post(seller, "/api/trade/returns/" + returnId + "/accept", Map.of());
        assertThat(stockOf(seller, variant)).containsExactly(80, 0, 80);

        // A rejected return releases the buyer's hold without moving stock.
        Response second = post(buyer, "/api/trade/invoices/" + id + "/returns", Map.of("reason", "Extra",
                "lines", List.of(Map.of("invoiceLineId", lineId, "quantity", 2))));
        long secondId = second.get("returns").get(1).get("id").asLong();
        assertThat(stockOf(buyer, buyerVariant)).containsExactly(20, 2, 18);
        assertThat(post(seller, "/api/trade/returns/" + secondId + "/reject", Map.of("note", "Not damaged")).status())
                .isEqualTo(200);
        assertThat(stockOf(buyer, buyerVariant)).containsExactly(20, 0, 20);
        assertThat(stockOf(seller, variant)).containsExactly(80, 0, 80);
    }

    @Test
    void searchShowsOnlyMinimalDetailsAndConnectionsAreNotDuplicated() {
        Session a = registerBusiness("Shri Ram Traders");
        Session b = registerBusiness("Shri Balaji Hardware");
        put(b, "/api/business", Map.of("name", "Shri Balaji Hardware", "phone", "98765 43210", "address", "Main Road"));

        Response found = get(a, "/api/network/search?q=shri");
        List<String> names = new ArrayList<>();
        found.body().forEach(r -> names.add(r.get("business").get("name").asText()));
        assertThat(names).contains("Shri Balaji Hardware").doesNotContain("Shri Ram Traders");
        JsonNode card = null;
        for (JsonNode r : found.body()) if (r.get("business").get("id").asLong() == b.businessId()) card = r.get("business");
        assertThat(card).isNotNull();
        assertThat(card.has("phone")).isFalse();
        assertThat(card.has("address")).isFalse();
        // By exact code or phone number too.
        String code = get(b, "/api/network/me").get("businessCode").asText();
        assertThat(get(a, "/api/network/search?q=" + code).body()).hasSize(1);
        assertThat(get(a, "/api/network/search?q=9876543210").body()).hasSize(1);

        Response req = post(a, "/api/network/connections", Map.of("businessId", b.businessId()));
        assertThat(req.status()).isEqualTo(201);
        assertThat(post(a, "/api/network/connections", Map.of("businessId", b.businessId())).status()).isEqualTo(409);
        // Only the receiving business can accept.
        assertThat(post(a, "/api/network/connections/" + req.get("id").asLong() + "/accept", null).status()).isEqualTo(403);
        // Asking back accepts the waiting request instead of creating a second one.
        Response back = post(b, "/api/network/connections", Map.of("businessId", a.businessId()));
        assertThat(back.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(get(a, "/api/network/connections").body()).hasSize(1);

        // Hidden businesses do not appear in search.
        put(b, "/api/network/me/discoverable", Map.of("discoverable", false));
        assertThat(get(a, "/api/network/search?q=Balaji").body()).isEmpty();
        // Staff without the right permissions cannot connect.
        Session staff = addEmployee(a, "STAFF");
        assertThat(post(staff, "/api/network/connections", Map.of("businessId", b.businessId())).status()).isEqualTo(403);
    }
}
