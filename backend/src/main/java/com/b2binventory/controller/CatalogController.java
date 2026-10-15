package com.b2binventory.controller;

import com.b2binventory.dto.CatalogDtos.*;
import com.b2binventory.service.AttributeService;
import com.b2binventory.service.CategoryService;
import com.b2binventory.service.UnitService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Catalog configuration")
public class CatalogController {

    private final UnitService units;
    private final CategoryService categories;
    private final AttributeService attributes;

    public CatalogController(UnitService units, CategoryService categories, AttributeService attributes) {
        this.units = units;
        this.categories = categories;
        this.attributes = attributes;
    }

    // ---- Units ----

    @GetMapping("/units")
    public List<UnitResponse> units(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return units.list(includeArchived);
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public UnitResponse createUnit(@Valid @RequestBody UnitRequest request) {
        return units.create(request);
    }

    @PutMapping("/units/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public UnitResponse updateUnit(@PathVariable Long id, @Valid @RequestBody UnitRequest request) {
        return units.update(id, request);
    }

    @PostMapping("/units/{id}/archive")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public UnitResponse archiveUnit(@PathVariable Long id) {
        return units.setArchived(id, true);
    }

    @PostMapping("/units/{id}/restore")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public UnitResponse restoreUnit(@PathVariable Long id) {
        return units.setArchived(id, false);
    }

    @GetMapping("/unit-conversions")
    public List<ConversionResponse> conversions() {
        return units.conversions();
    }

    @PostMapping("/unit-conversions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public ConversionResponse createConversion(@Valid @RequestBody ConversionRequest request) {
        return units.createConversion(request);
    }

    @DeleteMapping("/unit-conversions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public void deleteConversion(@PathVariable Long id) {
        units.deleteConversion(id);
    }

    @GetMapping("/unit-conversions/convert")
    public ConvertResult convert(@RequestParam BigDecimal quantity, @RequestParam Long fromUnitId,
                                 @RequestParam Long toUnitId, @RequestParam(required = false) Long productId) {
        return units.convert(quantity, fromUnitId, toUnitId, productId);
    }

    // ---- Categories ----

    @GetMapping("/categories")
    public List<CategoryResponse> categories(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return categories.tree(includeArchived);
    }

    @GetMapping("/categories/{id}")
    public CategoryResponse category(@PathVariable Long id) {
        return categories.get(id);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return categories.create(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categories.update(id, request);
    }

    @PostMapping("/categories/{id}/archive")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public CategoryResponse archiveCategory(@PathVariable Long id) {
        return categories.setArchived(id, true);
    }

    @PostMapping("/categories/{id}/restore")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public CategoryResponse restoreCategory(@PathVariable Long id) {
        return categories.setArchived(id, false);
    }

    /** Form fields for a product: pass no categoryId for products without a category. */
    @GetMapping("/form-schema")
    public FormSchema formSchema(@RequestParam(required = false) Long categoryId) {
        return categories.formSchema(categoryId);
    }

    // ---- Attribute definitions ----

    @GetMapping("/attributes")
    public List<AttributeResponse> attributes(@RequestParam(required = false) Long categoryId,
                                              @RequestParam(defaultValue = "false") boolean includeArchived) {
        return attributes.list(categoryId, includeArchived);
    }

    @PostMapping("/attributes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public AttributeResponse createAttribute(@Valid @RequestBody AttributeRequest request) {
        return attributes.create(request);
    }

    @PutMapping("/attributes/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public AttributeResponse updateAttribute(@PathVariable Long id, @Valid @RequestBody AttributeRequest request) {
        return attributes.update(id, request);
    }

    @PostMapping("/attributes/{id}/archive")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public AttributeResponse archiveAttribute(@PathVariable Long id) {
        return attributes.setArchived(id, true);
    }

    @PostMapping("/attributes/{id}/restore")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public AttributeResponse restoreAttribute(@PathVariable Long id) {
        return attributes.setArchived(id, false);
    }
}
