package com.b2binventory.dto;

import com.b2binventory.entity.FieldType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record UnitRequest(
            @NotBlank(message = "Code is required") @Size(max = 20) String code,
            @NotBlank(message = "Name is required") @Size(max = 80) String name,
            @Min(0) @Max(4) int decimals) {
    }

    public record UnitResponse(Long id, String code, String name, int decimals, boolean archived) {
    }

    public record ConversionRequest(
            @NotNull(message = "From unit is required") Long fromUnitId,
            @NotNull(message = "To unit is required") Long toUnitId,
            @NotNull(message = "Factor is required") @DecimalMin(value = "0.000001", message = "Factor must be positive") BigDecimal factor,
            Long productId) {
    }

    public record ConversionResponse(Long id, UnitResponse fromUnit, UnitResponse toUnit, BigDecimal factor,
                                     Long productId, String productName) {
    }

    public record ConvertResult(BigDecimal quantity, String fromUnit, BigDecimal result, String toUnit) {
    }

    public record CategoryRequest(
            @NotBlank(message = "Name is required") @Size(max = 120) String name,
            @Size(max = 1000) String description,
            Long parentId) {
    }

    public record CategoryResponse(Long id, String name, String description, Long parentId, String path,
                                   boolean archived, long productCount, int depth, List<CategoryResponse> children) {
    }

    public record AttributeRequest(
            @NotBlank(message = "Key is required")
            @Pattern(regexp = "[a-z][a-z0-9_]{0,59}", message = "Use lowercase letters, digits and underscores, starting with a letter")
            String key,
            @NotBlank(message = "Label is required") @Size(max = 120) String label,
            @NotNull(message = "Field type is required") FieldType fieldType,
            boolean required,
            List<@NotBlank @Size(max = 120) String> options,
            int sortOrder,
            Long categoryId) {
    }

    public record AttributeResponse(Long id, String key, String label, FieldType fieldType, boolean required,
                                    List<String> options, int sortOrder, Long categoryId, String categoryName,
                                    boolean archived) {
    }

    /** Everything the client needs to render the product form for a category. */
    public record FormSchema(Long categoryId, List<AttributeResponse> fields, boolean trackBatches,
                             boolean trackExpiry, boolean trackSerials, boolean trackDamagedReserved) {
    }
}
