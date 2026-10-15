package com.b2binventory.validation;

import com.b2binventory.entity.AttributeDefinition;
import com.b2binventory.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Validates product attribute values against the business's configured field definitions and
 * returns a normalized copy (numbers as BigDecimal, dates as ISO strings, multi-select as lists).
 * Keys that have no definition are rejected so typos do not silently create new data.
 */
@Component
public class AttributeValidator {

    public Map<String, Object> validate(Map<String, Object> input, List<AttributeDefinition> definitions) {
        Map<String, Object> values = input == null ? Map.of() : input;
        Map<String, AttributeDefinition> byKey = new LinkedHashMap<>();
        definitions.forEach(d -> byKey.put(d.getKey(), d));

        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();

        for (String key : values.keySet()) {
            if (!byKey.containsKey(key)) {
                errors.put("attributes." + key, "Unknown field for this category");
            }
        }

        for (AttributeDefinition def : byKey.values()) {
            Object raw = values.get(def.getKey());
            boolean empty = raw == null
                    || (raw instanceof String s && s.isBlank())
                    || (raw instanceof Collection<?> c && c.isEmpty());
            if (empty) {
                if (def.isRequired()) errors.put("attributes." + def.getKey(), def.getLabel() + " is required");
                continue;
            }
            try {
                result.put(def.getKey(), normalize(def, raw));
            } catch (IllegalArgumentException e) {
                errors.put("attributes." + def.getKey(), e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new BadRequestException("Some product details are invalid", errors);
        }
        return result;
    }

    private Object normalize(AttributeDefinition def, Object raw) {
        String label = def.getLabel();
        return switch (def.getFieldType()) {
            case TEXT -> {
                String s = String.valueOf(raw).trim();
                if (s.length() > 500) throw new IllegalArgumentException(label + " is too long");
                yield s;
            }
            case NUMBER -> {
                try {
                    yield raw instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(String.valueOf(raw).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(label + " must be a number");
                }
            }
            case DATE -> {
                try {
                    yield LocalDate.parse(String.valueOf(raw).trim()).toString();
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException(label + " must be a date (YYYY-MM-DD)");
                }
            }
            case BOOLEAN -> {
                if (raw instanceof Boolean b) yield b;
                String s = String.valueOf(raw).trim().toLowerCase();
                if (s.equals("true") || s.equals("false")) yield Boolean.parseBoolean(s);
                throw new IllegalArgumentException(label + " must be yes or no");
            }
            case DROPDOWN -> {
                String s = String.valueOf(raw).trim();
                if (!def.getOptions().contains(s)) throw new IllegalArgumentException(label + " must be one of " + def.getOptions());
                yield s;
            }
            case MULTI_SELECT -> {
                if (!(raw instanceof Collection<?> c)) throw new IllegalArgumentException(label + " must be a list");
                List<String> picked = new ArrayList<>();
                for (Object o : c) {
                    String s = String.valueOf(o).trim();
                    if (!def.getOptions().contains(s)) {
                        throw new IllegalArgumentException("'" + s + "' is not an option for " + label);
                    }
                    if (!picked.contains(s)) picked.add(s);
                }
                yield picked;
            }
        };
    }

    /** Variant attributes are free-form labels such as size or colour: only simple values are allowed. */
    public Map<String, Object> validateVariantAttributes(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (input == null) return result;
        Map<String, String> errors = new LinkedHashMap<>();
        input.forEach((k, v) -> {
            if (k == null || k.isBlank() || k.length() > 60) {
                errors.put("attributes", "Attribute names must be 1-60 characters");
            } else if (v instanceof String || v instanceof Number || v instanceof Boolean) {
                result.put(k.trim(), v instanceof String s ? s.trim() : v);
            } else if (v != null) {
                errors.put("attributes." + k, "Must be text, a number or yes/no");
            }
        });
        if (!errors.isEmpty()) throw new BadRequestException("Variant attributes are invalid", errors);
        return result;
    }
}
