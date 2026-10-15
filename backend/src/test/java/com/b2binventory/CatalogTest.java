package com.b2binventory;

import com.b2binventory.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogTest extends IntegrationTest {

    @Test
    void businessDefinesItsOwnCategoriesAndRequiredAttributes() {
        Session admin = registerBusiness("Fashion House");

        Response clothing = post(admin, "/api/categories", Map.of("name", "Clothing"));
        assertThat(clothing.status()).isEqualTo(201);
        long clothingId = clothing.get("id").asLong();
        Response shirts = post(admin, "/api/categories", Map.of("name", "Shirts", "parentId", clothingId));
        long shirtsId = shirts.get("id").asLong();
        assertThat(shirts.get("path").asText()).isEqualTo("Clothing / Shirts");

        // Fabric applies to all clothing (inherited by Shirts); size is specific to shirts.
        assertThat(post(admin, "/api/attributes", Map.of("key", "fabric", "label", "Fabric", "fieldType", "DROPDOWN",
                "required", true, "options", List.of("Cotton", "Linen"), "categoryId", clothingId)).status()).isEqualTo(201);
        assertThat(post(admin, "/api/attributes", Map.of("key", "sizes", "label", "Sizes", "fieldType", "MULTI_SELECT",
                "options", List.of("S", "M", "L"), "categoryId", shirtsId)).status()).isEqualTo(201);
        assertThat(post(admin, "/api/attributes", Map.of("key", "launch", "label", "Launch date", "fieldType", "DATE")).status())
                .isEqualTo(201);

        Response schema = get(admin, "/api/form-schema?categoryId=" + shirtsId);
        assertThat(schema.get("fields")).hasSize(3);

        // Missing required field is rejected by the server.
        Response missing = post(admin, "/api/products", Map.of("name", "Oxford shirt", "categoryId", shirtsId));
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.get("fieldErrors").has("attributes.fabric")).isTrue();

        // Invalid option and unknown key are rejected.
        Response invalid = post(admin, "/api/products", Map.of("name", "Oxford shirt", "categoryId", shirtsId,
                "attributes", Map.of("fabric", "Silk", "colour", "red")));
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.get("fieldErrors").has("attributes.fabric")).isTrue();
        assertThat(invalid.get("fieldErrors").has("attributes.colour")).isTrue();

        Response ok = post(admin, "/api/products", Map.of("name", "Oxford shirt", "categoryId", shirtsId,
                "sku", "OX-1", "attributes", Map.of("fabric", "Cotton", "sizes", List.of("M", "L"), "launch", "2026-05-01")));
        assertThat(ok.status()).isEqualTo(201);
        assertThat(ok.get("attributes").get("sizes")).hasSize(2);
        assertThat(ok.get("categoryPath").asText()).isEqualTo("Clothing / Shirts");

        // Duplicate SKU is reported on the field.
        Response dup = post(admin, "/api/products", Map.of("name", "Another", "sku", "ox-1"));
        assertThat(dup.status()).isEqualTo(400);
        assertThat(dup.get("fieldErrors").has("sku")).isTrue();

        // Filtering by the parent category includes subcategory products.
        Response list = get(admin, "/api/products?categoryId=" + clothingId);
        assertThat(list.get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void basicProductNeedsOnlyNameAndUsesDefaultUnit() {
        Session admin = registerBusiness("Corner Shop");
        Response p = post(admin, "/api/products", Map.of("name", "Notebook"));
        assertThat(p.status()).isEqualTo(201);
        assertThat(p.get("baseUnit").get("code").asText()).isEqualTo("pcs");
        assertThat(p.get("status").asText()).isEqualTo("OUT_OF_STOCK");

        Response edited = put(admin, "/api/products/" + p.get("id").asLong(), Map.of("name", "Notebook A5", "minStock", 3));
        assertThat(edited.status()).isEqualTo(200);
        assertThat(edited.get("name").asText()).isEqualTo("Notebook A5");
        assertThat(edited.get("variants").get(0).get("name").asText()).isEqualTo("Notebook A5");
    }

    @Test
    void archivedProductsAreHiddenFromDefaultList() {
        Session admin = registerBusiness("Archive Co");
        long id = post(admin, "/api/products", Map.of("name", "Old item")).get("id").asLong();
        post(admin, "/api/products", Map.of("name", "Current item"));
        assertThat(post(admin, "/api/products/" + id + "/archive", null).status()).isEqualTo(200);

        assertThat(get(admin, "/api/products").get("totalElements").asInt()).isEqualTo(1);
        assertThat(get(admin, "/api/products?archived=true").get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void unitConversionsWorkBothWays() {
        Session admin = registerBusiness("Bulk Foods");
        Response units = get(admin, "/api/units");
        long kg = 0, g = 0;
        for (var u : units.body()) {
            if (u.get("code").asText().equals("kg")) kg = u.get("id").asLong();
            if (u.get("code").asText().equals("g")) g = u.get("id").asLong();
        }
        assertThat(post(admin, "/api/unit-conversions", Map.of("fromUnitId", kg, "toUnitId", g, "factor", 1000)).status())
                .isEqualTo(201);
        assertThat(get(admin, "/api/unit-conversions/convert?quantity=2.5&fromUnitId=" + kg + "&toUnitId=" + g)
                .get("result").asDouble()).isEqualTo(2500.0);
        assertThat(get(admin, "/api/unit-conversions/convert?quantity=500&fromUnitId=" + g + "&toUnitId=" + kg)
                .get("result").asDouble()).isEqualTo(0.5);
    }

    @Test
    void staffCannotConfigureCatalogOrArchive() {
        Session admin = registerBusiness("Perm Catalog");
        Session staff = addEmployee(admin, "STAFF");
        long id = post(admin, "/api/products", Map.of("name", "Widget")).get("id").asLong();

        assertThat(post(staff, "/api/categories", Map.of("name", "X")).status()).isEqualTo(403);
        assertThat(post(staff, "/api/products", Map.of("name", "Y")).status()).isEqualTo(403);
        assertThat(post(staff, "/api/products/" + id + "/archive", null).status()).isEqualTo(403);
        assertThat(get(staff, "/api/products/" + id).status()).isEqualTo(200);
    }
}
