package com.b2binventory.service;

import com.b2binventory.domain.AppUser;
import com.b2binventory.domain.Business;
import com.b2binventory.domain.Category;
import com.b2binventory.domain.Product;
import com.b2binventory.domain.StockMovementType;
import com.b2binventory.dto.ProductRequest;
import com.b2binventory.repository.BusinessRepository;
import com.b2binventory.repository.CategoryRepository;
import com.b2binventory.repository.ProductRepository;
import com.b2binventory.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final StockService stockService;

    public ProductService(ProductRepository productRepository,
                          BusinessRepository businessRepository,
                          UserRepository userRepository,
                          CategoryRepository categoryRepository,
                          StockService stockService) {
        this.productRepository = productRepository;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.stockService = stockService;
    }

    public List<Product> getProducts(Long businessId, String category, String search) {
        if (search != null && !search.isBlank()) {
            return productRepository.findByBusinessIdAndNameContainingIgnoreCaseOrderByName(businessId, search);
        }
        if (category != null && !category.isBlank()) {
            return productRepository.findByBusinessIdAndCategoryNameIgnoreCaseOrderByUpdatedAtDesc(businessId, category);
        }
        return productRepository.findByBusinessIdOrderByUpdatedAtDesc(businessId);
    }

    public Product getProductById(Long id, Long businessId) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));
        
        if (!product.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("Access denied.");
        }
        
        return product;
    }

    @Transactional
    public Product createProduct(Long businessId, Long userId, ProductRequest request) {
        Business business = businessRepository.findById(businessId)
            .orElseThrow(() -> new IllegalArgumentException("Business not found"));
        
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("User does not belong to this business.");
        }

        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Product name is required.");
        }

        if (request.quantity() < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }

        Product product = new Product();
        product.setBusiness(business);
        applyCategory(product, businessId, request.category());
        product.setName(request.name());
        product.setBrand(request.brand());
        product.setSku(request.sku());
        product.setBarcode(request.barcode());
        product.setUnit(request.unit());
        product.setSize(request.size());
        product.setThickness(request.thickness());
        product.setColor(request.color());
        product.setModel(request.model());
        product.setDescription(request.description());
        product.setQuantity(request.quantity());
        product.setMinimumStock(request.minimumStock());
        product.setImageUrl(request.imageUrl());
        
        productRepository.save(product);

        if (request.quantity() > 0) {
            stockService.createStockMovement(
                business, user, product, 
                StockMovementType.ADD, 
                request.quantity(), 
                0, 
                request.quantity(), 
                "Opening stock"
            );
        }

        return product;
    }

    @Transactional
    public Product updateProduct(Long id, Long businessId, Long userId, ProductRequest request) {
        Product product = getProductById(id, businessId);
        
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("User does not belong to this business.");
        }

        if (request.name() != null && !request.name().isBlank()) {
            product.setName(request.name());
        }
        if (request.category() != null) {
            applyCategory(product, businessId, request.category());
        }
        if (request.brand() != null) {
            product.setBrand(request.brand());
        }
        if (request.sku() != null) {
            product.setSku(request.sku());
        }
        if (request.barcode() != null) {
            product.setBarcode(request.barcode());
        }
        if (request.unit() != null) {
            product.setUnit(request.unit());
        }
        if (request.size() != null) {
            product.setSize(request.size());
        }
        if (request.thickness() != null) {
            product.setThickness(request.thickness());
        }
        if (request.color() != null) {
            product.setColor(request.color());
        }
        if (request.model() != null) {
            product.setModel(request.model());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.minimumStock() >= 0) {
            product.setMinimumStock(request.minimumStock());
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(request.imageUrl());
        }

        product.touch();
        return productRepository.save(product);
    }

    public List<Product> getLowStockProducts(Long businessId) {
        return productRepository.findLowStockProducts(businessId);
    }

    public List<Product> getOutOfStockProducts(Long businessId) {
        return productRepository.findOutOfStockProducts(businessId);
    }

    @Transactional
    public void deleteProduct(Long id, Long businessId) {
        Product product = getProductById(id, businessId);
        product.setActive(false);
        product.touch();
        productRepository.save(product);
    }

    private void applyCategory(Product product, Long businessId, String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            product.setCategory(null);
            product.setCategoryName(null);
            return;
        }

        String normalizedCategoryName = categoryName.trim();
        Category category = categoryRepository
            .findByBusinessIdAndNameIgnoreCase(businessId, normalizedCategoryName)
            .orElse(null);

        if (category != null) {
            product.setCategory(category);
        } else {
            product.setCategory(null);
            product.setCategoryName(normalizedCategoryName);
        }
    }
}
