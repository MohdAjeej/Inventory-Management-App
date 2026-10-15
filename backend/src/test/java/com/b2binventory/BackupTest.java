package com.b2binventory;

import com.b2binventory.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static com.b2binventory.support.Fixtures.*;
import static com.b2binventory.support.SalesFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class BackupTest extends IntegrationTest {

    private byte[] download(Session s, int expectedStatus) throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.get("/api/backup")
                .header("Authorization", "Bearer " + s.token())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(expectedStatus);
        return r.getResponse().getContentAsByteArray();
    }

    private Response restore(Session s, byte[] body) throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/api/backup/restore")
                .header("Authorization", "Bearer " + s.token())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        byte[] content = r.getResponse().getContentAsByteArray();
        return new Response(r.getResponse().getStatus(), content.length == 0 ? null : json.readTree(content));
    }

    @Test
    void backupRestoresIntoANewBusinessWithTheSameData() throws Exception {
        Session admin = registerBusiness("Backup Co");
        post(admin, "/api/categories", Map.of("name", "Drinks"));
        Response p = post(admin, "/api/products", pricedProduct("Cola", 100, "8.50", "12.25"));
        long productId = p.get("id").asLong();
        long variant = defaultVariant(p.body());
        Response bought = post(admin, "/api/purchases", purchase(variant, 20, "9.10"));
        assertThat(bought.status()).as(bought.toString()).isEqualTo(201);
        Map<String, Object> s = paid(sale(line(variant, 3, "12.25")), "CASH", "36.75");
        s.put("customerName", "Asha");
        s.put("saveCustomer", true);
        Response sold = post(admin, "/api/sales", s);
        assertThat(sold.status()).as(sold.toString()).isEqualTo(201);
        String invoice = sold.get("invoiceNumber").asText();
        double stockBefore = available(get(admin, "/api/products/" + productId));

        byte[] file = download(admin, 200);
        JsonNode backup = json.readTree(file);
        assertThat(backup.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(backup.get("tables").get("sales").size()).isEqualTo(1);
        assertThat(backup.get("tables").has("users")).isFalse();
        assertThat(backup.get("tables").get("categories").size()).as(backup.get("tables").toString()).isEqualTo(1);
        assertThat(get(admin, "/api/categories").body().size()).isEqualTo(1);

        Response restored = restore(admin, file);
        assertThat(restored.status()).as(restored.toString()).isEqualTo(200);
        JsonNode user = restored.get("user");
        assertThat(user.get("businessId").asLong()).isNotEqualTo(admin.businessId());
        assertThat(user.get("businessName").asText()).isEqualTo("Backup Co (restored)");
        Session copy = session(restored, admin.email(), admin.password());

        JsonNode products = get(copy, "/api/products").get("content");
        assertThat(products.size()).isEqualTo(1);
        long newProductId = products.get(0).get("id").asLong();
        assertThat(newProductId).isNotEqualTo(productId);
        Response newProduct = get(copy, "/api/products/" + newProductId);
        assertThat(available(newProduct)).isEqualTo(stockBefore);
        assertThat(newProduct.get("averageCost").decimalValue())
                .isEqualByComparingTo(get(admin, "/api/products/" + productId).get("averageCost").decimalValue());

        JsonNode sales = get(copy, "/api/sales").get("content");
        assertThat(sales.size()).isEqualTo(1);
        assertThat(sales.get(0).get("invoiceNumber").asText()).isEqualTo(invoice);
        assertThat(get(copy, "/api/purchases").get("content").size()).isEqualTo(1);
        assertThat(get(copy, "/api/customers").body().size()).isEqualTo(1);
        assertThat(get(copy, "/api/categories").body().size()).isEqualTo(1);
        assertThat(get(copy, "/api/stock/movements").get("totalElements").asInt())
                .isEqualTo(get(admin, "/api/stock/movements").get("totalElements").asInt());

        // The restored business keeps working: numbering continues and stock moves.
        Response next = post(copy, "/api/sales", sale(line(defaultVariant(newProduct.body()), 1, "12.25")));
        assertThat(next.status()).as(next.toString()).isEqualTo(201);
        assertThat(next.get("invoiceNumber").asText()).isNotEqualTo(invoice);

        // The original is untouched.
        assertThat(get(admin, "/api/sales").get("totalElements").asInt()).isEqualTo(1);
        assertThat(available(get(admin, "/api/products/" + productId))).isEqualTo(stockBefore);
    }

    @Test
    void onlyAdminsCanExportAndBadFilesAreRejected() throws Exception {
        Session admin = registerBusiness("Guarded Co");
        Session staff = addEmployee(admin, "STAFF");
        download(staff, 403);

        assertThat(restore(admin, "{\"hello\":1}".getBytes()).status()).isEqualTo(400);
        assertThat(restore(admin, "not json".getBytes()).status()).isEqualTo(400);

        ObjectNode backup = (ObjectNode) json.readTree(download(admin, 200));
        backup.put("schemaVersion", "999");
        assertThat(restore(admin, json.writeValueAsBytes(backup)).status()).isEqualTo(400);
    }
}
