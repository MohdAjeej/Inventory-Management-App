package com.b2binventory.controller;

import com.b2binventory.domain.Product;
import com.b2binventory.dto.ProductRequest;
import com.b2binventory.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Product> getProducts(
        @RequestParam Long businessId,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String search
    ) {
        return productService.getProducts(businessId, category, search);
    }

    @GetMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Product getProduct(@PathVariable Long id, @RequestParam Long businessId) {
        return productService.getProductById(id, businessId);
    }

    @PostMapping
    @org.springframework.transaction.annotation.Transactional
    public Product createProduct(
        @RequestParam Long businessId,
        @RequestParam Long userId,
        @RequestBody ProductRequest request
    ) {
        return productService.createProduct(businessId, userId, request);
    }

    @PutMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public Product updateProduct(
        @PathVariable Long id,
        @RequestParam Long businessId,
        @RequestParam Long userId,
        @RequestBody ProductRequest request
    ) {
        return productService.updateProduct(id, businessId, userId, request);
    }

    @DeleteMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public void deleteProduct(@PathVariable Long id, @RequestParam Long businessId) {
        productService.deleteProduct(id, businessId);
    }

    @GetMapping("/low-stock")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Product> getLowStockProducts(@RequestParam Long businessId) {
        return productService.getLowStockProducts(businessId);
    }

    @GetMapping("/out-of-stock")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Product> getOutOfStockProducts(@RequestParam Long businessId) {
        return productService.getOutOfStockProducts(businessId);
    }
}
