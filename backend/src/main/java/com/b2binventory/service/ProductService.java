package com.b2binventory.service;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.ProductDtos.*;
import com.b2binventory.entity.*;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.*;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.validation.AttributeValidator;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final int MAX_IMAGES = 10;

    public record ProductFilter(String q, Long categoryId, Long warehouseId, StockStatus status, boolean archived,
                                String sort) {
    }

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final UnitRepository units;
    private final StockBalanceRepository balances;
    private final CategoryService categoryService;
    private final BusinessService businessService;
    private final AttributeValidator attributeValidator;
    private final StockService stockService;
    private final StockLevels stockLevels;
    private final FileStorageService files;
    private final AuditService audit;
    private final PartyService parties;

    public ProductService(ProductRepository products, ProductVariantRepository variants, ProductImageRepository images,
                          UnitRepository units, StockBalanceRepository balances, CategoryService categoryService,
                          BusinessService businessService, AttributeValidator attributeValidator,
                          StockService stockService, StockLevels stockLevels, FileStorageService files,
                          AuditService audit, PartyService parties) {
        this.products = products;
        this.variants = variants;
        this.images = images;
        this.units = units;
        this.balances = balances;
        this.categoryService = categoryService;
        this.businessService = businessService;
        this.attributeValidator = attributeValidator;
        this.stockService = stockService;
        this.stockLevels = stockLevels;
        this.files = files;
        this.audit = audit;
        this.parties = parties;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<ProductSummary> list(ProductFilter filter, int page, int size) {
        Long businessId = CurrentUser.get().businessId();
        BusinessSettings settings = businessService.settingsEntity(businessId);
        Set<Long> categoryIds = filter.categoryId() != null ? categoryService.withDescendants(filter.categoryId()) : null;
        Sort sort = switch (filter.sort() == null ? "name" : filter.sort()) {
            case "updated" -> Sort.by(Sort.Direction.DESC, "updatedAt");
            case "created" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by("name");
        };
        Page<Product> result = products.findAll(spec(businessId, filter, categoryIds, settings),
                PageRequest.of(page, size, sort.and(Sort.by("id"))));
        return summaries(result, settings, filter.warehouseId());
    }

    @Transactional(readOnly = true)
    public ProductDetail get(Long id) {
        return detail(find(id));
    }

    /** Finds products by exact SKU or barcode (for scanners). */
    @Transactional(readOnly = true)
    public List<ProductSummary> lookup(String code) {
        Long businessId = CurrentUser.get().businessId();
        List<Product> found = variants.findByCode(businessId, code.trim()).stream()
                .map(ProductVariant::getProduct).distinct().toList();
        if (found.isEmpty()) return List.of();
        Page<Product> page = new org.springframework.data.domain.PageImpl<>(found);
        return summaries(page, businessService.settingsEntity(businessId), null).content();
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public ProductDetail create(ProductRequest request) {
        CurrentUser me = CurrentUser.get();
        BusinessSettings settings = businessService.settingsEntity(me.businessId());

        Product p = new Product();
        p.setBusinessId(me.businessId());
        applyFields(p, request, settings);
        p.setAttributes(attributeValidator.validate(request.attributes(),
                categoryService.applicableAttributes(p.getCategory() != null ? p.getCategory().getId() : null)));
        products.save(p);

        ProductVariant v = new ProductVariant();
        v.setBusinessId(me.businessId());
        v.setProduct(p);
        v.setName(p.getName());
        v.setDefaultVariant(true);
        setCodes(v, request.sku(), request.barcode());
        variants.save(v);

        stockService.opening(v, request.openingStock());
        // Opening stock is valued at the purchase price until goods are purchased through the app.
        if (p.getPurchasePrice() != null && request.openingStock() != null && request.openingStock().quantity() != null
                && request.openingStock().quantity().signum() > 0) {
            p.setAverageCost(p.getPurchasePrice().setScale(4));
        }

        audit.record(me.businessId(), me.membershipId(), "PRODUCT_CREATED", "Product", p.getId(),
                Map.of("name", p.getName()));
        return detail(p);
    }

    @Transactional
    public ProductDetail update(Long id, ProductRequest request) {
        CurrentUser me = CurrentUser.get();
        BusinessSettings settings = businessService.settingsEntity(me.businessId());
        Product p = find(id);
        String previousName = p.getName();
        Long previousUnit = p.getBaseUnit().getId();
        boolean previousSerials = p.isTrackSerials();
        boolean previousBatches = p.isTrackBatches();
        java.math.BigDecimal previousSale = p.getSalePrice();
        java.math.BigDecimal previousPurchase = p.getPurchasePrice();
        applyFields(p, request, settings);
        if (!java.util.Objects.equals(previousSale, p.getSalePrice()) || !java.util.Objects.equals(previousPurchase, p.getPurchasePrice())) {
            // Older sales and purchases keep their own price snapshots; this only changes the defaults.
            Map<String, Object> change = new java.util.LinkedHashMap<>();
            change.put("salePrice", previousSale + " -> " + p.getSalePrice());
            change.put("purchasePrice", previousPurchase + " -> " + p.getPurchasePrice());
            audit.record(me.businessId(), me.membershipId(), "PRODUCT_PRICE_CHANGED", "Product", id, change);
        }
        boolean hasStock = balances.productHasStock(id);
        if (hasStock && !previousUnit.equals(p.getBaseUnit().getId())) {
            throw new ConflictException("The unit cannot be changed while the product has stock");
        }
        if (hasStock && (previousSerials != p.isTrackSerials() || previousBatches != p.isTrackBatches())) {
            throw new ConflictException("Batch or serial tracking cannot be changed while the product has stock");
        }
        p.setAttributes(attributeValidator.validate(request.attributes(),
                categoryService.applicableAttributes(p.getCategory() != null ? p.getCategory().getId() : null)));
        ProductVariant defaultVariant = defaultVariant(p);
        // The main variant mirrors the product name unless it was renamed separately.
        if (defaultVariant.getName().equals(previousName)) {
            defaultVariant.setName(p.getName());
        }
        setCodes(defaultVariant, request.sku(), request.barcode());
        audit.record(me.businessId(), me.membershipId(), "PRODUCT_UPDATED", "Product", id, Map.of("name", p.getName()));
        return detail(p);
    }

    @Transactional
    public ProductDetail setArchived(Long id, boolean archived) {
        CurrentUser me = CurrentUser.get();
        Product p = find(id);
        p.setArchived(archived);
        audit.record(me.businessId(), me.membershipId(), archived ? "PRODUCT_ARCHIVED" : "PRODUCT_RESTORED",
                "Product", id, Map.of("name", p.getName()));
        return detail(p);
    }

    @Transactional
    public ProductDetail addVariant(Long productId, VariantRequest request) {
        CurrentUser me = CurrentUser.get();
        Product p = find(productId);
        ProductVariant v = new ProductVariant();
        v.setBusinessId(me.businessId());
        v.setProduct(p);
        applyVariant(v, request);
        variants.save(v);
        p.setUpdatedAt(java.time.Instant.now());
        audit.record(me.businessId(), me.membershipId(), "VARIANT_CREATED", "ProductVariant", v.getId(),
                Map.of("productId", productId, "name", v.getName()));
        return detail(p);
    }

    @Transactional
    public ProductDetail updateVariant(Long variantId, VariantRequest request) {
        CurrentUser me = CurrentUser.get();
        ProductVariant v = variant(variantId);
        applyVariant(v, request);
        audit.record(me.businessId(), me.membershipId(), "VARIANT_UPDATED", "ProductVariant", variantId,
                Map.of("name", v.getName()));
        return detail(v.getProduct());
    }

    @Transactional
    public ProductDetail setVariantArchived(Long variantId, boolean archived) {
        CurrentUser me = CurrentUser.get();
        ProductVariant v = variant(variantId);
        if (archived && v.isDefaultVariant()) {
            throw new ConflictException("The main variant cannot be archived; archive the product instead");
        }
        if (archived && balances.variantHasStock(variantId)) {
            throw new ConflictException("Reduce this variant's stock to zero before archiving it");
        }
        v.setArchived(archived);
        audit.record(me.businessId(), me.membershipId(), archived ? "VARIANT_ARCHIVED" : "VARIANT_RESTORED",
                "ProductVariant", variantId, Map.of("name", v.getName()));
        return detail(v.getProduct());
    }

    @Transactional
    public ProductDetail addImage(Long productId, MultipartFile file) {
        CurrentUser me = CurrentUser.get();
        Product p = find(productId);
        if (images.countByProductId(productId) >= MAX_IMAGES) {
            throw new BadRequestException("A product can have at most " + MAX_IMAGES + " images");
        }
        ProductImage image = new ProductImage();
        image.setBusinessId(me.businessId());
        image.setProduct(p);
        image.setUrl(files.storeImage(file));
        image.setSortOrder((int) images.countByProductId(productId));
        images.save(image);
        audit.record(me.businessId(), me.membershipId(), "PRODUCT_IMAGE_ADDED", "Product", productId, null);
        return detail(p);
    }

    @Transactional
    public ProductDetail deleteImage(Long productId, Long imageId) {
        CurrentUser me = CurrentUser.get();
        Product p = find(productId);
        ProductImage image = images.findByIdAndBusinessId(imageId, me.businessId())
                .filter(i -> i.getProduct().getId().equals(productId))
                .orElseThrow(() -> new NotFoundException("Image", imageId));
        images.delete(image);
        files.deleteQuietly(image.getUrl());
        audit.record(me.businessId(), me.membershipId(), "PRODUCT_IMAGE_REMOVED", "Product", productId, null);
        return detail(p);
    }

    // ------------------------------------------------------------------ helpers

    public Product find(Long id) {
        return products.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Product", id));
    }

    private ProductVariant variant(Long id) {
        return variants.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Product variant", id));
    }

    private ProductVariant defaultVariant(Product p) {
        return variants.findByProductIdOrderByIdAsc(p.getId()).stream()
                .filter(ProductVariant::isDefaultVariant).findFirst()
                .orElseThrow(() -> new IllegalStateException("Product " + p.getId() + " has no default variant"));
    }

    private void applyFields(Product p, ProductRequest r, BusinessSettings settings) {
        p.setName(r.name().trim());
        p.setBrand(AuthService.blankToNull(r.brand()));
        p.setModel(AuthService.blankToNull(r.model()));
        p.setDescription(AuthService.blankToNull(r.description()));
        p.setMinStock(r.minStock());
        p.setSalePrice(r.salePrice());
        p.setTaxRate(r.taxRate());
        // Members who cannot see costs never receive the purchase price, so they cannot change it either.
        if (com.b2binventory.security.Permissions.canSeeCost()) p.setPurchasePrice(r.purchasePrice());
        p.setSupplier(r.supplierId() != null ? parties.supplier(r.supplierId()) : null);
        if (r.categoryId() != null) {
            Category c = categoryService.find(r.categoryId());
            if (c.isArchived() && (p.getCategory() == null || !p.getCategory().getId().equals(c.getId()))) {
                throw new BadRequestException("Validation failed", Map.of("categoryId", "This category is archived"));
            }
            p.setCategory(c);
        } else {
            p.setCategory(null);
        }
        Unit unit;
        if (r.baseUnitId() != null) {
            unit = units.findVisibleById(r.baseUnitId(), p.getBusinessId())
                    .orElseThrow(() -> new NotFoundException("Unit", r.baseUnitId()));
        } else if (p.getBaseUnit() != null) {
            unit = p.getBaseUnit();
        } else {
            unit = units.findVisibleByCode(p.getBusinessId(), "PCS").stream().filter(u -> !u.isArchived()).findFirst()
                    .or(() -> units.findVisible(p.getBusinessId(), false).stream().findFirst())
                    .orElseThrow(() -> new BadRequestException("Validation failed", Map.of("baseUnitId", "Choose a unit")));
        }
        if (unit.isArchived() && (p.getBaseUnit() == null || !p.getBaseUnit().getId().equals(unit.getId()))) {
            throw new BadRequestException("Validation failed", Map.of("baseUnitId", "This unit is archived"));
        }
        p.setBaseUnit(unit);
        p.setTrackBatches(r.trackBatches() && settings.isTrackBatches());
        p.setTrackSerials(r.trackSerials() && settings.isTrackSerials());
        if (p.isTrackSerials() && unit.getDecimals() > 0) {
            throw new BadRequestException("Validation failed", Map.of("baseUnitId",
                    "Serial-tracked products need a whole-number unit"));
        }
    }

    private void applyVariant(ProductVariant v, VariantRequest r) {
        v.setName(r.name().trim());
        v.setMinStock(r.minStock());
        v.setAttributes(attributeValidator.validateVariantAttributes(r.attributes()));
        setCodes(v, r.sku(), r.barcode());
    }

    private void setCodes(ProductVariant v, String sku, String barcode) {
        String s = AuthService.blankToNull(sku);
        String b = AuthService.blankToNull(barcode);
        long self = v.getId() == null ? -1L : v.getId();
        Map<String, String> errors = new LinkedHashMap<>();
        if (s != null && variants.skuTaken(v.getBusinessId(), s, self)) errors.put("sku", "This SKU is already used");
        if (b != null && variants.barcodeTaken(v.getBusinessId(), b, self)) errors.put("barcode", "This barcode is already used");
        if (!errors.isEmpty()) throw new BadRequestException("Validation failed", errors);
        v.setSku(s);
        v.setBarcode(b);
    }

    private Specification<Product> spec(Long businessId, ProductFilter f, Set<Long> categoryIds, BusinessSettings settings) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("businessId"), businessId));
            p.add(cb.equal(root.get("archived"), f.archived()));
            if (categoryIds != null) p.add(root.get("category").get("id").in(categoryIds));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                Subquery<Long> codes = query.subquery(Long.class);
                Root<ProductVariant> v = codes.from(ProductVariant.class);
                codes.select(v.get("product").get("id")).where(cb.or(
                        cb.like(cb.lower(v.get("sku")), like),
                        cb.like(cb.lower(v.get("barcode")), like),
                        cb.like(cb.lower(v.get("name")), like)));
                p.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("brand")), like),
                        cb.like(cb.lower(root.get("model")), like),
                        root.get("id").in(codes)));
            }
            if (f.status() != null) {
                Subquery<BigDecimal> available = query.subquery(BigDecimal.class);
                Root<StockBalance> b = available.from(StockBalance.class);
                Join<StockBalance, ProductVariant> bv = b.join("variant");
                List<Predicate> bp = new ArrayList<>();
                bp.add(cb.equal(bv.get("product"), root));
                bp.add(cb.isFalse(bv.get("archived")));
                if (f.warehouseId() != null) bp.add(cb.equal(b.get("warehouse").get("id"), f.warehouseId()));
                available.select(cb.coalesce(cb.sum(cb.diff(cb.diff(b.get("onHand"), b.get("reserved")), b.get("damaged"))),
                        BigDecimal.ZERO)).where(bp.toArray(Predicate[]::new));
                Expression<BigDecimal> qty = cb.coalesce(available, BigDecimal.ZERO);
                Expression<Integer> threshold = cb.coalesce(root.get("minStock"), settings.getDefaultLowStockThreshold());
                Expression<BigDecimal> thresholdDecimal = threshold.as(BigDecimal.class);
                p.add(switch (f.status()) {
                    case OUT_OF_STOCK -> cb.le(qty, BigDecimal.ZERO);
                    case LOW_STOCK -> cb.and(cb.gt(qty, BigDecimal.ZERO), cb.le(qty, thresholdDecimal));
                    case IN_STOCK -> cb.gt(qty, thresholdDecimal);
                });
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private PageResponse<ProductSummary> summaries(Page<Product> page, BusinessSettings settings, Long warehouseId) {
        List<Long> ids = page.getContent().stream().map(Product::getId).toList();
        Map<Long, List<ProductVariant>> variantsByProduct = ids.isEmpty() ? Map.of()
                : variants.findForProducts(ids).stream().collect(Collectors.groupingBy(v -> v.getProduct().getId()));
        Map<Long, String> firstImage = new HashMap<>();
        if (!ids.isEmpty()) images.findForProducts(ids).forEach(i -> firstImage.putIfAbsent(i.getProduct().getId(), i.getUrl()));
        List<Long> variantIds = variantsByProduct.values().stream().flatMap(List::stream)
                .filter(v -> !v.isArchived()).map(ProductVariant::getId).toList();
        Map<Long, StockTotals> totals = stockLevels.byVariant(variantIds, warehouseId);

        return PageResponse.of(page, p -> {
            List<ProductVariant> vs = variantsByProduct.getOrDefault(p.getId(), List.of());
            ProductVariant main = vs.stream().filter(ProductVariant::isDefaultVariant).findFirst().orElse(null);
            List<ProductVariant> active = vs.stream().filter(v -> !v.isArchived()).toList();
            StockTotals sum = StockLevels.sum(active.stream()
                    .map(v -> totals.getOrDefault(v.getId(), StockTotals.ZERO)).toList());
            int threshold = StockLevels.threshold(p, settings);
            return new ProductSummary(p.getId(), p.getName(), p.getBrand(), p.getModel(),
                    p.getCategory() != null ? p.getCategory().getId() : null,
                    p.getCategory() != null ? p.getCategory().getName() : null,
                    p.getBaseUnit().getCode(), main != null ? main.getSku() : null, main != null ? main.getBarcode() : null,
                    firstImage.get(p.getId()), sum, threshold, StockLevels.status(sum.available(), threshold),
                    active.size(), p.isArchived(), p.getUpdatedAt(), p.getSalePrice(),
                    com.b2binventory.security.Permissions.canSeeCost() ? p.getPurchasePrice() : null);
        });
    }

    private ProductDetail detail(Product p) {
        boolean showCost = com.b2binventory.security.Permissions.canSeeCost();
        BusinessSettings settings = businessService.settingsEntity(p.getBusinessId());
        List<ProductVariant> vs = variants.findByProductIdOrderByIdAsc(p.getId());
        Map<Long, StockTotals> totals = stockLevels.byVariant(vs.stream().map(ProductVariant::getId).toList(), null);
        List<VariantResponse> variantResponses = vs.stream().map(v -> {
            StockTotals t = totals.getOrDefault(v.getId(), StockTotals.ZERO);
            int threshold = StockLevels.threshold(v, settings);
            return new VariantResponse(v.getId(), v.getName(), v.getSku(), v.getBarcode(), v.getAttributes(),
                    v.getMinStock(), threshold, v.isDefaultVariant(), v.isArchived(), t,
                    StockLevels.status(t.available(), threshold));
        }).toList();
        StockTotals sum = StockLevels.sum(vs.stream().filter(v -> !v.isArchived())
                .map(v -> totals.getOrDefault(v.getId(), StockTotals.ZERO)).toList());
        int threshold = StockLevels.threshold(p, settings);
        List<ImageResponse> imageResponses = images.findByProductIdOrderBySortOrderAscIdAsc(p.getId()).stream()
                .map(i -> new ImageResponse(i.getId(), i.getUrl())).toList();
        List<BalanceResponse> balanceResponses = balances.findByProduct(p.getId()).stream()
                .filter(b -> b.getOnHand().signum() != 0 || b.getReserved().signum() != 0 || b.getDamaged().signum() != 0)
                .map(StockService::toResponse).toList();
        return new ProductDetail(p.getId(), p.getName(), p.getBrand(), p.getModel(), p.getDescription(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                categoryService.path(p.getCategory()), UnitService.toResponse(p.getBaseUnit()), p.getMinStock(),
                threshold, p.getAttributes(), p.isTrackBatches(), p.isTrackSerials(), p.isArchived(), sum,
                StockLevels.status(sum.available(), threshold), variantResponses, imageResponses, balanceResponses,
                p.getCreatedAt(), p.getUpdatedAt(), p.getSalePrice(), showCost ? p.getPurchasePrice() : null,
                showCost ? p.getAverageCost() : null, p.getTaxRate(),
                p.getSupplier() != null ? p.getSupplier().getId() : null,
                p.getSupplier() != null ? p.getSupplier().getName() : null,
                showCost ? pricing(p, sum.available()) : null);
    }

    static Pricing pricing(Product p, java.math.BigDecimal available) {
        java.math.BigDecimal cost = p.costBasis();
        java.math.BigDecimal price = p.getSalePrice();
        java.math.BigDecimal stockQty = available.max(java.math.BigDecimal.ZERO);
        java.math.BigDecimal perUnit = cost != null && price != null ? price.subtract(cost) : null;
        java.math.BigDecimal margin = perUnit != null && price.signum() > 0
                ? perUnit.multiply(Money.HUNDRED).divide(price, 1, java.math.RoundingMode.HALF_UP) : null;
        return new Pricing(cost, p.getAverageCost() != null ? "AVERAGE" : cost != null ? "PURCHASE_PRICE" : null,
                perUnit == null ? null : Money.amount(perUnit), margin,
                cost == null ? null : Money.amount(stockQty.multiply(cost)),
                price == null ? null : Money.amount(stockQty.multiply(price)),
                perUnit == null ? null : Money.amount(stockQty.multiply(perUnit)));
    }
}
