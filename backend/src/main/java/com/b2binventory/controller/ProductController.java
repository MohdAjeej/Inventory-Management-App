package com.b2binventory.controller;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.ProductDtos.*;
import com.b2binventory.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Products")
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public PageResponse<ProductSummary> list(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) Long warehouseId,
                                             @RequestParam(required = false) StockStatus status,
                                             @RequestParam(defaultValue = "false") boolean archived,
                                             @RequestParam(defaultValue = "name") String sort,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return products.list(new ProductService.ProductFilter(q, categoryId, warehouseId, status, archived, sort),
                page, Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/products/lookup")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public List<ProductSummary> lookup(@RequestParam String code) {
        return products.lookup(code);
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ProductDetail get(@PathVariable Long id) {
        return products.get(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ProductDetail create(@Valid @RequestBody ProductRequest request) {
        return products.create(request);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    public ProductDetail update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, request);
    }

    @PostMapping("/products/{id}/archive")
    @PreAuthorize("hasAuthority('PRODUCT_ARCHIVE')")
    public ProductDetail archive(@PathVariable Long id) {
        return products.setArchived(id, true);
    }

    @PostMapping("/products/{id}/restore")
    @PreAuthorize("hasAuthority('PRODUCT_ARCHIVE')")
    public ProductDetail restore(@PathVariable Long id) {
        return products.setArchived(id, false);
    }

    @PostMapping("/products/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    public ProductDetail addVariant(@PathVariable Long id, @Valid @RequestBody VariantRequest request) {
        return products.addVariant(id, request);
    }

    @PutMapping("/variants/{variantId}")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    public ProductDetail updateVariant(@PathVariable Long variantId, @Valid @RequestBody VariantRequest request) {
        return products.updateVariant(variantId, request);
    }

    @PostMapping("/variants/{variantId}/archive")
    @PreAuthorize("hasAuthority('PRODUCT_ARCHIVE')")
    public ProductDetail archiveVariant(@PathVariable Long variantId) {
        return products.setVariantArchived(variantId, true);
    }

    @PostMapping("/variants/{variantId}/restore")
    @PreAuthorize("hasAuthority('PRODUCT_ARCHIVE')")
    public ProductDetail restoreVariant(@PathVariable Long variantId) {
        return products.setVariantArchived(variantId, false);
    }

    @PostMapping(value = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    public ProductDetail addImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return products.addImage(id, file);
    }

    @DeleteMapping("/products/{id}/images/{imageId}")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    public ProductDetail deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        return products.deleteImage(id, imageId);
    }
}
