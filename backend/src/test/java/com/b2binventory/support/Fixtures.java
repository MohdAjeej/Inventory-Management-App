package com.b2binventory.support;

import com.b2binventory.support.IntegrationTest.Response;
import com.b2binventory.support.IntegrationTest.Session;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small helpers shared by the inventory tests. */
public final class Fixtures {

    private Fixtures() {
    }

    public static Map<String, Object> product(String name, Object openingQty) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        if (openingQty != null) body.put("openingStock", Map.of("quantity", openingQty));
        return body;
    }

    public static Map<String, Object> op(String type, long variantId, Object qty) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("variantId", variantId);
        body.put("quantity", qty);
        if (type.equals("ADJUST")) body.put("reason", "Correction");
        return body;
    }

    public static long defaultVariant(JsonNode product) {
        for (JsonNode v : product.get("variants")) {
            if (v.get("isDefault").asBoolean()) return v.get("id").asLong();
        }
        throw new AssertionError("no default variant");
    }

    public static double available(Response product) {
        return product.get("stock").get("available").asDouble();
    }

    public static long warehouseId(IntegrationTest test, Session s, String name) {
        Response list = test.get(s, "/api/warehouses");
        for (JsonNode w : list.body()) {
            if (w.get("name").asText().equals(name)) return w.get("id").asLong();
        }
        throw new AssertionError("warehouse not found: " + name);
    }

    public static List<String> types(JsonNode page) {
        return java.util.stream.StreamSupport.stream(page.get("content").spliterator(), false)
                .map(n -> n.get("type").asText()).toList();
    }
}
