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
        assertThat(p.get("baseUnit").get("code").asText()).isEqualTo("PCS");
        assertThat(p.get("baseUnit").get("standard").asBoolean()).isTrue();
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

    private long unitId(Session s, String code) {
        for (var u : get(s, "/api/units").body()) {
            if (u.get("code").asText().equals(code)) return u.get("id").asLong();
        }
        throw new AssertionError("No unit " + code);
    }

    private static List<String> codes(com.fasterxml.jackson.databind.JsonNode units) {
        return units.findValues("code").stream().map(c -> c.asText()).toList();
    }

    @Test
    void standardUnitsAreSharedGroupedAndSearchable() {
        Session admin = registerBusiness("Unit Lister");
        var units = get(admin, "/api/units").body();
        assertThat(units.size()).isGreaterThanOrEqualTo(51);
        assertThat(codes(units)).contains("PCS", "DOZ", "DRM", "KG", "QTL", "OZ", "KM", "YD", "M²", "SQ FT", "HA", "KL",
                "CU IN", "SEC", "WK", "YR");
        assertThat(get(admin, "/api/unit-categories").body().size()).isEqualTo(7);

        assertThat(codes(get(admin, "/api/units?q=kilo").body())).containsExactlyInAnyOrder("KG", "KM", "KL");
        var byCode = get(admin, "/api/units?q=doz").body();
        assertThat(byCode.size()).isEqualTo(1);
        assertThat(byCode.get(0).get("name").asText()).isEqualTo("Dozen");
        var weights = get(admin, "/api/units?category=WEIGHT").body();
        assertThat(weights.size()).isEqualTo(7);
        assertThat(weights.findValues("category").stream().allMatch(c -> c.asText().equals("WEIGHT"))).isTrue();
    }

    @Test
    void standardUnitsConvertWithinTheirDimensionOnly() {
        Session admin = registerBusiness("Bulk Foods");
        long kg = unitId(admin, "KG"), g = unitId(admin, "G"), m = unitId(admin, "M"), bag = unitId(admin, "BAG");
        long dozen = unitId(admin, "DOZ"), pcs = unitId(admin, "PCS"), l = unitId(admin, "L"), ml = unitId(admin, "ML");

        assertThat(get(admin, "/api/unit-conversions/convert?quantity=2.5&fromUnitId=" + kg + "&toUnitId=" + g)
                .get("result").asDouble()).isEqualTo(2500.0);
        assertThat(get(admin, "/api/unit-conversions/convert?quantity=500&fromUnitId=" + g + "&toUnitId=" + kg)
                .get("result").asDouble()).isEqualTo(0.5);
        assertThat(get(admin, "/api/unit-conversions/convert?quantity=1&fromUnitId=" + l + "&toUnitId=" + ml)
                .get("result").asDouble()).isEqualTo(1000.0);
        assertThat(get(admin, "/api/unit-conversions/convert?quantity=2&fromUnitId=" + dozen + "&toUnitId=" + pcs)
                .get("result").asDouble()).isEqualTo(24.0);

        // Built in, so defining it again is refused; different dimensions are refused outright.
        assertThat(post(admin, "/api/unit-conversions", Map.of("fromUnitId", kg, "toUnitId", g, "factor", 1000)).status())
                .isEqualTo(400);
        Response kgToM = post(admin, "/api/unit-conversions", Map.of("fromUnitId", kg, "toUnitId", m, "factor", 2));
        assertThat(kgToM.status()).isEqualTo(400);
        assertThat(kgToM.get("message").asText()).contains("measure different things");
        assertThat(get(admin, "/api/unit-conversions/convert?quantity=1&fromUnitId=" + kg + "&toUnitId=" + m).status())
                .isEqualTo(400);

        // A container with no fixed size may be defined against any unit: 1 bag = 50 kg.
        assertThat(post(admin, "/api/unit-conversions", Map.of("fromUnitId", bag, "toUnitId", kg, "factor", 50)).status())
                .isEqualTo(201);
        assertThat(get(admin, "/api/unit-conversions/convert?quantity=3&fromUnitId=" + bag + "&toUnitId=" + kg)
                .get("result").asDouble()).isEqualTo(150.0);
        assertThat(get(admin, "/api/unit-conversions/convert?quantity=100&fromUnitId=" + kg + "&toUnitId=" + bag)
                .get("result").asDouble()).isEqualTo(2.0);
    }

    @Test
    void customUnitsAreBusinessScopedUniqueAndEditable() {
        Session admin = registerBusiness("Gold Smith");
        Session other = registerBusiness("Other Smith");

        // Codes and names of standard units are taken, whatever the case.
        assertThat(post(admin, "/api/units", Map.of("code", "kg", "name", "Kilo", "decimals", 0)).status()).isEqualTo(409);
        assertThat(post(admin, "/api/units", Map.of("code", "KGS", "name", "kilogram", "decimals", 0)).status()).isEqualTo(409);

        Response tola = post(admin, "/api/units", Map.of("code", "TOLA", "name", "Tola", "decimals", 3,
                "category", "WEIGHT", "baseFactor", "0.0116638"));
        assertThat(tola.status()).as(tola.toString()).isEqualTo(201);
        assertThat(tola.get("standard").asBoolean()).isFalse();
        long tolaId = tola.get("id").asLong();
        assertThat(post(admin, "/api/units", Map.of("code", "tola", "name", "Tola 2", "decimals", 0)).status()).isEqualTo(409);

        // A custom unit with a base factor converts like a standard one.
        assertThat(get(admin, "/api/unit-conversions/convert?quantity=10&fromUnitId=" + tolaId + "&toUnitId=" + unitId(admin, "G"))
                .get("result").asDouble()).isEqualTo(117.0);

        Response edited = put(admin, "/api/units/" + tolaId, Map.of("code", "TOLA", "name", "Tola (gold)", "decimals", 3,
                "category", "WEIGHT", "baseFactor", "0.0116638"));
        assertThat(edited.status()).isEqualTo(200);
        assertThat(edited.get("name").asText()).isEqualTo("Tola (gold)");
        assertThat(post(admin, "/api/units/" + tolaId + "/archive", null).get("archived").asBoolean()).isTrue();

        // Standard units are protected.
        long kg = unitId(admin, "KG");
        assertThat(put(admin, "/api/units/" + kg, Map.of("code", "KG", "name", "Kilo", "decimals", 2)).status()).isEqualTo(400);
        assertThat(post(admin, "/api/units/" + kg + "/archive", null).status()).isEqualTo(400);

        // Another business neither sees nor can use the custom unit, and may reuse its code.
        assertThat(codes(get(other, "/api/units?includeArchived=true").body())).doesNotContain("TOLA");
        assertThat(post(other, "/api/products", Map.of("name", "Ring", "baseUnitId", tolaId)).status()).isEqualTo(404);
        assertThat(post(other, "/api/units", Map.of("code", "TOLA", "name", "Tola", "decimals", 0)).status()).isEqualTo(201);

        // Products may use standard units.
        Response ring = post(admin, "/api/products", Map.of("name", "Ring", "baseUnitId", unitId(admin, "G")));
        assertThat(ring.status()).isEqualTo(201);
        assertThat(ring.get("baseUnit").get("name").asText()).isEqualTo("Gram");
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
