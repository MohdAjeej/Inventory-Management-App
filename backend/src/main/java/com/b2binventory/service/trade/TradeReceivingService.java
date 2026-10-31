package com.b2binventory.service.trade;

import com.b2binventory.dto.CatalogDtos.CategoryRequest;
import com.b2binventory.dto.CatalogDtos.UnitRequest;
import com.b2binventory.dto.ProductDtos.ProductDetail;
import com.b2binventory.dto.ProductDtos.ProductRequest;
import com.b2binventory.dto.ProductDtos.StockTotals;
import com.b2binventory.dto.ProductDtos.VariantRequest;
import com.b2binventory.dto.PurchaseDtos.PurchaseLineRequest;
import com.b2binventory.dto.PurchaseDtos.PurchaseRequest;
import com.b2binventory.dto.PurchaseDtos.PurchaseResponse;
import com.b2binventory.dto.SaleDtos.SaleLineRequest;
import com.b2binventory.dto.SaleDtos.SaleRequest;
import com.b2binventory.dto.SaleDtos.SaleResponse;
import com.b2binventory.dto.TradeDtos.*;
import com.b2binventory.entity.*;
import com.b2binventory.exception.ApiException;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.*;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.security.Permission;
import com.b2binventory.security.Permissions;
import com.b2binventory.service.*;
import com.b2binventory.service.notification.NotificationService;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * The buyer's side of a trade invoice: deciding which of the buyer's products each line is received into, and
 * approving. Approval posts everything in one database transaction: the seller's held stock is released and sold
 * (an ordinary sale in the seller's books), and the goods are received into the buyer's stock as an ordinary
 * purchase at the agreed prices. If any step fails nothing is saved and the invoice stays pending.
 */
@Service
public class TradeReceivingService {

    private final TradeInvoiceRepository invoices;
    private final TradeInvoiceService invoiceService;
    private final ProductMappingRepository mappings;
    private final ProductVariantRepository variants;
    private final WarehouseRepository warehouses;
    private final UnitRepository units;
    private final CategoryRepository categories;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final ProductService productService;
    private final UnitService unitService;
    private final CategoryService categoryService;
    private final SaleService saleService;
    private final PurchaseService purchaseService;
    private final StockLevels stockLevels;
    private final BusinessService businessService;
    private final NetworkService network;
    private final ActingBusiness acting;
    private final NotificationService notifications;
    private final AuditService audit;
    private final EntityManager em;

    public TradeReceivingService(TradeInvoiceRepository invoices, TradeInvoiceService invoiceService,
                                 ProductMappingRepository mappings, ProductVariantRepository variants,
                                 WarehouseRepository warehouses, UnitRepository units, CategoryRepository categories,
                                 CustomerRepository customers, SupplierRepository suppliers, ProductService productService,
                                 UnitService unitService, CategoryService categoryService, SaleService saleService,
                                 PurchaseService purchaseService, StockLevels stockLevels, BusinessService businessService,
                                 NetworkService network, ActingBusiness acting, NotificationService notifications,
                                 AuditService audit, EntityManager em) {
        this.invoices = invoices;
        this.invoiceService = invoiceService;
        this.mappings = mappings;
        this.variants = variants;
        this.warehouses = warehouses;
        this.units = units;
        this.categories = categories;
        this.customers = customers;
        this.suppliers = suppliers;
        this.productService = productService;
        this.unitService = unitService;
        this.categoryService = categoryService;
        this.saleService = saleService;
        this.purchaseService = purchaseService;
        this.stockLevels = stockLevels;
        this.businessService = businessService;
        this.network = network;
        this.acting = acting;
        this.notifications = notifications;
        this.audit = audit;
        this.em = em;
    }

    // ------------------------------------------------------------------ preview

    /** How each line would be received if approved now, so the buyer can check or change it first. */
    @Transactional(readOnly = true)
    public ReceivePreview preview(Long id, Long warehouseId) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        CurrentUser me = CurrentUser.get();
        TradeInvoice inv = invoices.findVisible(id, me.businessId())
                .filter(i -> i.getBuyerBusinessId().equals(me.businessId()))
                .orElseThrow(() -> new NotFoundException("Invoice", id));
        Warehouse warehouse = buyerWarehouse(warehouseId, me.businessId());
        List<LineProposal> lines = new ArrayList<>();
        for (TradeInvoiceLine l : inv.getLines()) {
            if (l.getBuyerVariantId() != null) {
                ProductVariant v = variants.findByIdAndBusinessId(l.getBuyerVariantId(), me.businessId()).orElse(null);
                lines.add(new LineProposal(l.getId(), "RECEIVED", v == null ? null : candidate(v, warehouse.getId()), List.of()));
                continue;
            }
            Match m = match(l, me.businessId(), inv.getSellerBusinessId());
            List<Candidate> candidates = m.candidates().stream().map(v -> candidate(v, warehouse.getId())).toList();
            lines.add(new LineProposal(l.getId(), m.mode(), m.target() == null ? null : candidate(m.target(), warehouse.getId()),
                    candidates));
        }
        return new ReceivePreview(inv.getId(), warehouse.getId(), warehouse.getName(), lines);
    }

    // ------------------------------------------------------------------ approve

    @Transactional
    public TradeInvoiceResponse approve(Long id, ApproveRequest r) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        CurrentUser me = CurrentUser.get();
        TradeInvoice inv = invoiceService.lockAsBuyer(id, me);
        // A repeated approval (double tap, retry after a timeout) returns the result of the first one.
        if (inv.getStatus() == TradeInvoiceStatus.POSTED) return invoiceService.get(id);
        if (inv.getStatus() != TradeInvoiceStatus.PENDING_APPROVAL) throw new ConflictException(TradeInvoiceService.closedMessage(inv));
        network.requireConnected(inv.getSellerBusinessId(), inv.getBuyerBusinessId());

        Long buyerId = me.businessId();
        Long sellerId = inv.getSellerBusinessId();
        Business seller = network.business(sellerId);
        Business buyer = network.business(buyerId);
        Warehouse buyerWarehouse = buyerWarehouse(r == null ? null : r.warehouseId(), buyerId);

        Map<Long, LineChoice> choices = new HashMap<>();
        if (r != null && r.lines() != null) {
            Set<Long> lineIds = new HashSet<>();
            inv.getLines().forEach(l -> lineIds.add(l.getId()));
            for (LineChoice c : r.lines()) {
                if (!lineIds.contains(c.lineId())) throw new BadRequestException("Line " + c.lineId() + " is not on this invoice");
                choices.put(c.lineId(), c);
            }
        }

        // 1. Decide where every line goes before changing anything.
        Long supplierId = linkedSupplier(buyerId, seller);
        Map<String, String> review = new LinkedHashMap<>();
        Map<Long, ProductVariant> targets = new HashMap<>();
        List<TradeInvoiceLine> lines = inv.getLines();
        for (int i = 0; i < lines.size(); i++) {
            TradeInvoiceLine l = lines.get(i);
            LineChoice choice = choices.get(l.getId());
            ProductVariant target;
            if (choice != null && choice.variantId() != null) {
                target = variants.findByIdAndBusinessId(choice.variantId(), buyerId)
                        .orElseThrow(() -> new NotFoundException("Product variant", choice.variantId()));
                checkUsable(target, l, "lines[" + i + "]");
            } else if (choice != null && choice.createNew()) {
                target = null;
            } else {
                Match m = match(l, buyerId, sellerId);
                if ("REVIEW".equals(m.mode())) {
                    review.put("lines[" + i + "]", "Several of your products could be " + label(l) + ". Choose one.");
                    continue;
                }
                target = m.target();
            }
            targets.put(l.getId(), target);
        }
        if (!review.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "NEEDS_REVIEW",
                    "Choose which of your products to receive some items into", review);
        }
        for (TradeInvoiceLine l : lines) {
            if (targets.get(l.getId()) == null) targets.put(l.getId(), createProduct(l, buyerId, supplierId));
        }

        // 2. Seller side, as the member who sent the invoice: release the held stock and record the sale.
        Long sellerActor = acting.actor(sellerId, inv.getCreatedBy(), Permission.SALE_RECORD);
        SaleResponse sale = acting.runAs(sellerActor, () -> {
            invoiceService.releaseSellerStock(inv, "Invoice " + inv.getInvoiceNumber() + " approved by " + buyer.getName());
            Long customerId = linkedCustomer(sellerId, buyer);
            List<SaleLineRequest> items = lines.stream().map(l -> new SaleLineRequest(l.getSellerVariantId(),
                    inv.getSellerWarehouseId(), l.getQuantity(), l.getUnitPrice(), l.getDiscount(), l.getTaxRate(),
                    null, null, null)).toList();
            return saleService.record(new SaleRequest(items, customerId, null, null, false, inv.getInvoiceNumber(), null,
                    null, "Business invoice to " + buyer.getName(), requestId("trade-sale", inv.getId())));
        });

        // 3. Buyer side: receive the goods at the agreed prices as an ordinary purchase.
        BusinessSettings buyerSettings = businessService.settingsEntity(buyerId);
        List<PurchaseLineRequest> purchaseLines = lines.stream().map(l -> {
            ProductVariant v = targets.get(l.getId());
            boolean batches = buyerSettings.isTrackBatches() && v.getProduct().isTrackBatches();
            return new PurchaseLineRequest(v.getId(), buyerWarehouse.getId(), l.getQuantity(), l.getUnitPrice(),
                    l.getDiscount(), l.getTaxRate(), batches ? inv.getInvoiceNumber() : null, null, null);
        }).toList();
        PurchaseResponse purchase = purchaseService.record(new PurchaseRequest(purchaseLines, supplierId, null, false,
                inv.getInvoiceNumber(), null, null, "Business invoice from " + seller.getName(),
                requestId("trade-purchase", inv.getId())));
        if (sale.total().compareTo(inv.getTotal()) != 0 || purchase.total().compareTo(inv.getTotal()) != 0) {
            // Both documents are worked out from the same lines; a difference would be a bug, so stop here.
            throw new IllegalStateException("Posted totals differ from the invoice: sale " + sale.total()
                    + ", purchase " + purchase.total() + ", invoice " + inv.getTotal());
        }

        // 4. Remember the links and close the invoice.
        for (TradeInvoiceLine l : lines) {
            ProductVariant v = targets.get(l.getId());
            l.setBuyerVariantId(v.getId());
            ProductMapping mapping = mappings.findByBuyerBusinessIdAndSellerVariantId(buyerId, l.getSellerVariantId())
                    .orElseGet(() -> {
                        ProductMapping pm = new ProductMapping();
                        pm.setBuyerBusinessId(buyerId);
                        pm.setSellerBusinessId(sellerId);
                        pm.setSellerVariantId(l.getSellerVariantId());
                        return pm;
                    });
            mapping.setBuyerVariantId(v.getId());
            mappings.save(mapping);
        }
        inv.setStatus(TradeInvoiceStatus.POSTED);
        inv.setSellerSaleId(sale.id());
        inv.setBuyerPurchaseId(purchase.id());
        inv.setBuyerWarehouseId(buyerWarehouse.getId());
        inv.setRespondedBy(me.membershipId());
        inv.setRespondedAt(Instant.now());
        invoiceService.event(inv, null, "APPROVED", me, "Stock received into " + buyerWarehouse.getName());

        notifications.notify(sellerId, null, TradeInvoiceService.INVOICE_APPROVED,
                buyer.getName() + " approved invoice " + inv.getInvoiceNumber(),
                "Stock has been deducted and the sale is recorded as " + sale.invoiceNumber() + ".",
                Map.of("tradeInvoiceId", inv.getId()), "TI:" + inv.getId() + ":APPROVED", Duration.ofDays(365));
        audit.record(buyerId, me.membershipId(), "TRADE_INVOICE_APPROVED", "TradeInvoice", inv.getId(),
                Map.of("number", inv.getInvoiceNumber(), "purchaseId", purchase.id()));
        audit.record(sellerId, sellerActor, "TRADE_INVOICE_POSTED", "TradeInvoice", inv.getId(),
                Map.of("number", inv.getInvoiceNumber(), "saleId", sale.id(), "approvedByBusinessId", buyerId));
        return invoiceService.get(id);
    }

    // ------------------------------------------------------------------ product links

    @Transactional(readOnly = true)
    public List<ProductMappingResponse> mappings() {
        Permissions.require(Permission.PURCHASE_MANAGE);
        Long me = CurrentUser.get().businessId();
        List<ProductMapping> list = mappings.findByBuyerBusinessIdOrderByUpdatedAtDesc(me);
        Map<Long, Business> sellers = network.businessesById(list.stream().map(ProductMapping::getSellerBusinessId).toList());
        List<ProductMappingResponse> result = new ArrayList<>();
        for (ProductMapping m : list) {
            ProductVariant mine = variants.findByIdAndBusinessId(m.getBuyerVariantId(), me).orElse(null);
            if (mine == null) continue;
            // The seller's product name as it appeared on the latest invoice line, not read from their catalogue.
            List<String> names = em.createQuery("select l.productName || case when l.defaultVariant = false then ' · ' || l.variantName else '' end "
                            + "from TradeInvoiceLine l where l.sellerVariantId = ?1 and l.invoice.buyerBusinessId = ?2 "
                            + "order by l.id desc", String.class)
                    .setParameter(1, m.getSellerVariantId()).setParameter(2, me).setMaxResults(1).getResultList();
            result.add(new ProductMappingResponse(m.getId(), NetworkService.card(sellers.get(m.getSellerBusinessId())),
                    m.getSellerVariantId(), names.isEmpty() ? null : names.get(0), mine.getId(), mine.getProduct().getId(),
                    mine.getProduct().getName(), mine.isDefaultVariant() ? null : mine.getName(), m.getUpdatedAt()));
        }
        return result;
    }

    @Transactional
    public void deleteMapping(Long id) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        CurrentUser me = CurrentUser.get();
        ProductMapping m = mappings.findByIdAndBuyerBusinessId(id, me.businessId())
                .orElseThrow(() -> new NotFoundException("Product link", id));
        mappings.delete(m);
        audit.record(me.businessId(), me.membershipId(), "PRODUCT_LINK_REMOVED", "ProductMapping", id, Map.of());
    }

    // ------------------------------------------------------------------ matching

    /** mode: MAPPED, MATCHED, NEW or REVIEW; target is set for MAPPED and MATCHED. */
    record Match(String mode, ProductVariant target, List<ProductVariant> candidates) {
    }

    /**
     * Finds the buyer's product for an invoice line. Seller product ids are never used directly; they only key the
     * buyer's saved links. Otherwise: the same barcode, then the same product name, variant and unit. A match is
     * used only when exactly one product fits; several fits are left for the buyer to choose.
     */
    Match match(TradeInvoiceLine l, Long buyerId, Long sellerId) {
        Optional<ProductMapping> mapped = mappings.findByBuyerBusinessIdAndSellerVariantId(buyerId, l.getSellerVariantId());
        if (mapped.isPresent()) {
            Optional<ProductVariant> v = variants.findByIdAndBusinessId(mapped.get().getBuyerVariantId(), buyerId)
                    .filter(x -> usable(x, l));
            if (v.isPresent()) return new Match("MAPPED", v.get(), List.of());
        }
        if (l.getBarcode() != null && !l.getBarcode().isBlank()) {
            List<ProductVariant> byBarcode = em.createQuery("select v from ProductVariant v join fetch v.product p "
                            + "join fetch p.baseUnit where v.businessId = ?1 and lower(v.barcode) = ?2", ProductVariant.class)
                    .setParameter(1, buyerId).setParameter(2, l.getBarcode().trim().toLowerCase(Locale.ROOT))
                    .getResultList().stream().filter(v -> usable(v, l)).toList();
            if (byBarcode.size() == 1) return new Match("MATCHED", byBarcode.get(0), List.of());
            if (byBarcode.size() > 1) return new Match("REVIEW", null, byBarcode);
        }
        List<ProductVariant> byName = em.createQuery("select v from ProductVariant v join fetch v.product p join fetch p.baseUnit "
                        + "where v.businessId = ?1 and lower(p.name) = ?2 and (lower(v.name) = ?3 or (?4 = true and v.defaultVariant = true))",
                        ProductVariant.class)
                .setParameter(1, buyerId).setParameter(2, l.getProductName().trim().toLowerCase(Locale.ROOT))
                .setParameter(3, l.getVariantName().trim().toLowerCase(Locale.ROOT)).setParameter(4, l.isDefaultVariant())
                .getResultList().stream().filter(v -> usable(v, l)).toList();
        if (byName.size() == 1) return new Match("MATCHED", byName.get(0), List.of());
        if (byName.size() > 1) return new Match("REVIEW", null, byName);
        return new Match("NEW", null, List.of());
    }

    private static boolean usable(ProductVariant v, TradeInvoiceLine l) {
        return !v.isArchived() && !v.getProduct().isArchived()
                && v.getProduct().getBaseUnit().getCode().equalsIgnoreCase(l.getUnitCode());
    }

    private void checkUsable(ProductVariant v, TradeInvoiceLine l, String field) {
        if (v.isArchived() || v.getProduct().isArchived()) {
            throw new BadRequestException("Validation failed", Map.of(field, v.getProduct().getName() + " is archived"));
        }
        String unit = v.getProduct().getBaseUnit().getCode();
        if (!unit.equalsIgnoreCase(l.getUnitCode())) {
            // Never convert silently: 25 sheets must not become 25 boxes.
            throw new BadRequestException("Validation failed", Map.of(field, label(l) + " is invoiced in " + l.getUnitCode()
                    + " but " + v.getProduct().getName() + " is counted in " + unit + ". Choose a product counted in "
                    + l.getUnitCode() + " or create a new one."));
        }
    }

    // ------------------------------------------------------------------ creating the buyer's product

    /** Adds the invoiced product to the buyer's catalogue with the same name, variant, unit, codes and specifications. */
    private ProductVariant createProduct(TradeInvoiceLine l, Long buyerId, Long supplierId) {
        // The buyer's own or a standard unit with the same code; otherwise a new custom unit (renamed if the name is taken).
        Unit unit = units.findVisibleByCode(buyerId, l.getUnitCode()).stream().findFirst().orElseGet(() -> {
            String name = units.findVisibleByName(buyerId, l.getUnitName()).isEmpty()
                    ? l.getUnitName() : l.getUnitName() + " (" + l.getUnitCode() + ")";
            Long unitId = unitService.create(new UnitRequest(l.getUnitCode(), name, l.getUnitDecimals())).id();
            return units.findByIdAndBusinessId(unitId, buyerId).orElseThrow();
        });
        if (unit.isArchived() && !unit.isStandard()) unit.setArchived(false);
        Long categoryId = null;
        if (l.getCategoryName() != null && !l.getCategoryName().isBlank()) {
            categoryId = categories.findAllForBusiness(buyerId).stream()
                    .filter(c -> !c.isArchived() && c.getName().equalsIgnoreCase(l.getCategoryName().trim()))
                    .map(Category::getId).findFirst()
                    .orElseGet(() -> categoryService.create(new CategoryRequest(l.getCategoryName().trim(), null, null)).id());
        }
        Map<String, Object> specs = specifications(l);
        String sku = free(buyerId, l.getSku());
        String barcode = free(buyerId, l.getBarcode());

        if (!l.isDefaultVariant()) {
            // A size or other variant: add it to the buyer's product of the same name when there is exactly one.
            List<Product> sameName = em.createQuery("select p from Product p where p.businessId = ?1 and p.archived = false "
                            + "and lower(p.name) = ?2 and p.baseUnit.id = ?3", Product.class)
                    .setParameter(1, buyerId).setParameter(2, l.getProductName().trim().toLowerCase(Locale.ROOT))
                    .setParameter(3, unit.getId()).getResultList();
            Long productId = sameName.size() == 1 ? sameName.get(0).getId()
                    : productService.create(productRequest(l, categoryId, unit.getId(), null, null, supplierId)).id();
            ProductDetail detail = productService.addVariant(productId, new VariantRequest(l.getVariantName(), sku, barcode, specs, null));
            Long variantId = detail.variants().stream().filter(v -> v.name().equals(l.getVariantName()))
                    .reduce((a, b) -> b).orElseThrow().id();
            return variants.findByIdAndBusinessId(variantId, buyerId).orElseThrow();
        }
        ProductDetail detail = productService.create(productRequest(l, categoryId, unit.getId(), sku, barcode, supplierId));
        ProductVariant v = variants.findByIdAndBusinessId(detail.variants().get(0).id(), buyerId).orElseThrow();
        if (!specs.isEmpty()) v.setAttributes(new LinkedHashMap<>(specs));
        return v;
    }

    private static ProductRequest productRequest(TradeInvoiceLine l, Long categoryId, Long unitId, String sku, String barcode,
                                                 Long supplierId) {
        return new ProductRequest(l.getProductName(), categoryId, unitId, l.getBrand(), l.getModel(), l.getDescription(),
                null, null, sku, barcode, false, false, null, null, l.getUnitPrice(), l.getTaxRate(), supplierId);
    }

    /**
     * The seller's specifications (size, thickness, colour...) as simple text/number values on the buyer's
     * variant. The seller's custom-field keys are kept; lists become comma-separated text.
     */
    private static Map<String, Object> specifications(TradeInvoiceLine l) {
        Map<String, Object> specs = new LinkedHashMap<>();
        Map<String, Object> all = new LinkedHashMap<>(l.getAttributes());
        all.putAll(l.getVariantAttributes());
        all.forEach((k, v) -> {
            if (k == null || k.isBlank() || v == null) return;
            String key = k.length() > 60 ? k.substring(0, 60) : k;
            if (v instanceof String || v instanceof Number || v instanceof Boolean) specs.put(key, v);
            else if (v instanceof Collection<?> c) specs.put(key, String.join(", ", c.stream().map(String::valueOf).toList()));
            else specs.put(key, String.valueOf(v));
        });
        return specs;
    }

    /** Keeps the seller's SKU or barcode only if the buyer does not already use it for something else. */
    private String free(Long buyerId, String code) {
        if (code == null || code.isBlank()) return null;
        return variants.findByCode(buyerId, code.trim()).isEmpty() ? code.trim() : null;
    }

    // ------------------------------------------------------------------ parties

    /** The seller's customer record for the buyer business (created the first time). Runs as the seller. */
    private Long linkedCustomer(Long sellerId, Business buyer) {
        List<Customer> found = em.createQuery("select c from Customer c where c.businessId = ?1 and c.linkedBusinessId = ?2",
                Customer.class).setParameter(1, sellerId).setParameter(2, buyer.getId()).getResultList();
        Customer c = found.isEmpty() ? new Customer() : found.get(0);
        if (found.isEmpty()) {
            c.setBusinessId(sellerId);
            c.setLinkedBusinessId(buyer.getId());
        }
        c.setName(buyer.getName());
        c.setPhone(buyer.getPhone());
        c.setEmail(buyer.getEmail());
        c.setAddress(address(buyer));
        c.setArchived(false);
        return customers.save(c).getId();
    }

    /** The buyer's supplier record for the seller business (created the first time). */
    private Long linkedSupplier(Long buyerId, Business seller) {
        List<Supplier> found = em.createQuery("select s from Supplier s where s.businessId = ?1 and s.linkedBusinessId = ?2",
                Supplier.class).setParameter(1, buyerId).setParameter(2, seller.getId()).getResultList();
        Supplier s = found.isEmpty() ? new Supplier() : found.get(0);
        if (found.isEmpty()) {
            s.setBusinessId(buyerId);
            s.setLinkedBusinessId(seller.getId());
        }
        s.setName(seller.getName());
        s.setPhone(seller.getPhone());
        s.setEmail(seller.getEmail());
        s.setAddress(address(seller));
        s.setTaxId(seller.getTaxId());
        s.setArchived(false);
        return suppliers.save(s).getId();
    }

    private static String address(Business b) {
        String joined = String.join(", ", java.util.stream.Stream.of(b.getAddress(), b.getCity(), b.getState(), b.getPostalCode())
                .filter(x -> x != null && !x.isBlank()).toList());
        return joined.isEmpty() ? null : (joined.length() > 500 ? joined.substring(0, 500) : joined);
    }

    // ------------------------------------------------------------------ helpers

    private Warehouse buyerWarehouse(Long warehouseId, Long buyerId) {
        if (warehouseId != null) {
            return warehouses.findByIdAndBusinessId(warehouseId, buyerId).filter(w -> !w.isArchived())
                    .orElseThrow(() -> new NotFoundException("Warehouse", warehouseId));
        }
        return warehouses.findFirstByBusinessIdAndDefaultWarehouseTrue(buyerId)
                .or(() -> warehouses.findByBusinessIdAndArchivedFalseOrderByNameAsc(buyerId).stream().findFirst())
                .orElseThrow(() -> new BadRequestException("Create a warehouse first"));
    }

    private Candidate candidate(ProductVariant v, Long warehouseId) {
        StockTotals t = stockLevels.byVariant(List.of(v.getId()), warehouseId).getOrDefault(v.getId(), StockTotals.ZERO);
        Product p = v.getProduct();
        return new Candidate(v.getId(), p.getId(), p.getName(), v.isDefaultVariant() ? null : v.getName(),
                p.getBaseUnit().getCode(), t.available());
    }

    private static String label(TradeInvoiceLine l) {
        return l.isDefaultVariant() ? l.getProductName() : l.getProductName() + " (" + l.getVariantName() + ")";
    }

    /** The same request id every time for the same invoice, so a retried approval cannot post twice. */
    static UUID requestId(String kind, Long id) {
        return UUID.nameUUIDFromBytes((kind + ":" + id).getBytes(StandardCharsets.UTF_8));
    }
}
