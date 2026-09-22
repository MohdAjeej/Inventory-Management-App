package com.b2binventory.controller;

import com.b2binventory.domain.Business;
import com.b2binventory.domain.Category;
import com.b2binventory.dto.CategoryRequest;
import com.b2binventory.dto.CategoryResponse;
import com.b2binventory.dto.CategoryStatsResponse;
import com.b2binventory.repository.BusinessRepository;
import com.b2binventory.repository.CategoryRepository;
import com.b2binventory.repository.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {
    
    @Autowired
    private CategoryRepository categoryRepository;
    
    @Autowired
    private BusinessRepository businessRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    // Get all categories for a business with pagination
    @GetMapping("/business/{businessId}")
    public ResponseEntity<Map<String, Object>> getCategoriesByBusiness(
            @PathVariable Long businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Category> categoriesPage;
            
            if (search != null && !search.isEmpty()) {
                categoriesPage = categoryRepository.searchCategories(businessId, search, pageable);
            } else if (status != null && !status.isEmpty()) {
                categoriesPage = categoryRepository.findByBusinessIdAndStatus(businessId, status, pageable);
            } else {
                categoriesPage = categoryRepository.findByBusinessId(businessId, pageable);
            }
            
            List<CategoryResponse> categories = categoriesPage.getContent().stream()
                    .map(CategoryResponse::fromEntity)
                    .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("categories", categories);
            response.put("currentPage", categoriesPage.getNumber());
            response.put("totalItems", categoriesPage.getTotalElements());
            response.put("totalPages", categoriesPage.getTotalPages());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Get category statistics for a business
    @GetMapping("/business/{businessId}/stats")
    public ResponseEntity<CategoryStatsResponse> getCategoryStats(@PathVariable Long businessId) {
        try {
            long totalCategories = categoryRepository.countByBusinessId(businessId);
            long activeCategories = categoryRepository.countByBusinessIdAndStatus(businessId, "Active");
            long inactiveCategories = categoryRepository.countByBusinessIdAndStatus(businessId, "Inactive");
            long categoriesInUse = categoryRepository.countCategoriesWithProducts(businessId);
            long totalProducts = productRepository.countByBusinessId(businessId);
            
            CategoryStatsResponse stats = new CategoryStatsResponse(
                totalCategories,
                activeCategories,
                inactiveCategories,
                categoriesInUse,
                totalProducts
            );
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Get category by ID
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return categoryRepository.findById(id)
                .map(category -> ResponseEntity.ok(CategoryResponse.fromEntity(category)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    // Create new category
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        try {
            // Check if business exists
            Business business = businessRepository.findById(request.getBusinessId())
                    .orElseThrow(() -> new RuntimeException("Business not found"));
            
            // Check if category name already exists for this business
            if (categoryRepository.existsByNameAndBusinessId(request.getName(), request.getBusinessId())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            
            Category category = new Category();
            category.setName(request.getName());
            category.setDescription(request.getDescription());
            category.setIconUrl(request.getIconUrl());
            category.setStatus(request.getStatus() != null ? request.getStatus() : "Active");
            category.setBusiness(business);
            
            Category savedCategory = categoryRepository.save(category);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(CategoryResponse.fromEntity(savedCategory));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Update category
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        
        try {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            
            // Check if new name conflicts with existing categories (excluding current one)
            if (!category.getName().equals(request.getName()) &&
                categoryRepository.existsByNameAndBusinessId(request.getName(), request.getBusinessId())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            
            category.setName(request.getName());
            category.setDescription(request.getDescription());
            category.setIconUrl(request.getIconUrl());
            category.setStatus(request.getStatus());
            
            Category updatedCategory = categoryRepository.save(category);
            return ResponseEntity.ok(CategoryResponse.fromEntity(updatedCategory));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Toggle category status (activate/deactivate)
    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<CategoryResponse> toggleCategoryStatus(@PathVariable Long id) {
        try {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            
            String newStatus = category.getStatus().equals("Active") ? "Inactive" : "Active";
            category.setStatus(newStatus);
            
            Category updatedCategory = categoryRepository.save(category);
            return ResponseEntity.ok(CategoryResponse.fromEntity(updatedCategory));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Delete category
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteCategory(@PathVariable Long id) {
        try {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            
            // Check if category has products
            if (category.getProductCount() > 0) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Cannot delete category with existing products");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            
            categoryRepository.delete(category);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Category deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
