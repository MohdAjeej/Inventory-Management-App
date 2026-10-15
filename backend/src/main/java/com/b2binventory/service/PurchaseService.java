package com.b2binventory.service;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.PurchaseDtos.*;
import com.b2binventory.dto.SaleDtos.PaymentInput;
import com.b2binventory.dto.SaleDtos.PaymentRequest;
import com.b2binventory.entity.*;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.InsufficientStockException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.*;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.security.Permission;
import com.b2binventory.security.Permissions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.b2binventory.service.AuthService.blankToNull;
import static com.b2binventory.service.Money.amount;
import static com.b2binventory.service.Money.orZero;

/**
 * Goods received from suppliers. Recording a purchase adds stock (PURCHASE movements), updates each product's
 * weighted-average cost and default purchase price, and records any payment, all in one transaction.
 */
@Service
public class PurchaseService {

    private final PurchaseRepository purchases;
    private final PaymentRepository payments;
    private final ProductVariantRepository variants;
    private final StockBalanceRepository balances;
    private final MembershipRepository memberships;
    private final SequenceRepository sequences;
    private final StockService stock;
    private final SaleService saleService;
    private final BusinessService businessService;
    private final PartyService parties;
    private final AuditService audit;
    private final EntityManager em;

    public PurchaseService(PurchaseRepository purchases, PaymentRepository payments, ProductVariantRepository variants,
                           StockBalanceRepository balances, MembershipRepository memberships, SequenceRepository sequences,
                           StockService stock, SaleService saleService, BusinessService businessService,
                           PartyService parties, AuditService audit, EntityManager em) {
        this.purchases = purchases;
        this.payments = payments;
        this.variants = variants;
        this.balances = balances;
        this.memberships = memberships;
        this.sequences = sequences;
        this.stock = stock;
        this.saleService = saleService;
        this.businessService = businessService;
        this.parties = parties;
        this.audit = audit;
        this.em = em;
    }

    @Transactional
    public PurchaseResponse record(PurchaseRequest r) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        CurrentUser me = CurrentUser.get();
        Long businessId = me.businessId();
        Optional<Purchase> existing = purchases.findByBusinessIdAndClientRequestId(businessId, r.clientRequestId());
        if (existing.isPresent()) return get(existing.get().getId());

        Purchase pu = new Purchase();
        pu.setBusinessId(businessId);
        pu.setPurchaseNumber(String.format("PUR-%05d", sequences.next(businessId, "PURCHASE")));
        pu.setReferenceNumber(blankToNull(r.referenceNumber()));
        pu.setPurchasedAt(SaleService.notInFuture(r.purchasedAt(), "purchasedAt", "Purchase date"));
        pu.setNotes(blankToNull(r.notes()));
        pu.setActor(memberships.getReferenceById(me.membershipId()));
        pu.setClientRequestId(r.clientRequestId());
        String supplierName = blankToNull(r.supplierName());
        if (r.supplierId() != null) {
            Supplier s = parties.supplier(r.supplierId());
            pu.setSupplier(s);
            if (supplierName == null) supplierName = s.getName();
        } else if (r.saveSupplier() && supplierName != null) {
            pu.setSupplier(parties.quickSupplier(supplierName));
        }
        pu.setSupplierName(supplierName);

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < r.items().size(); i++) order.add(i);
        order.sort(Comparator.comparing((Integer i) -> r.items().get(i).variantId())
                .thenComparing(i -> Objects.requireNonNullElse(r.items().get(i).warehouseId(), 0L)));
        PurchaseItem[] lines = new PurchaseItem[r.items().size()];
        for (int i : order) lines[i] = line(pu, r.items().get(i), i, businessId);
        pu.getItems().addAll(Arrays.asList(lines));

        BigDecimal subtotal = BigDecimal.ZERO, discounts = BigDecimal.ZERO, tax = BigDecimal.ZERO, total = BigDecimal.ZERO;
        for (PurchaseItem it : lines) {
            subtotal = subtotal.add(it.getGrossAmount());
            discounts = discounts.add(it.getDiscount());
            tax = tax.add(it.getTaxAmount());
            total = total.add(it.getLineTotal());
        }
        pu.setSubtotal(subtotal);
        pu.setDiscountTotal(discounts);
        pu.setTaxTotal(tax);
        pu.setTotal(total);
        purchases.save(pu);

        PaymentInput pay = r.payment();
        if (pay != null && pay.amount().signum() > 0) {
            if (pay.amount().compareTo(total) > 0) {
                throw new BadRequestException("Validation failed", Map.of("payment.amount",
                        "Amount paid cannot be more than the purchase total " + total.toPlainString()));
            }
            Payment p = saleService.payment(PaymentKind.PURCHASE_PAYMENT, pay.amount(), pay.method(), pay.reference(),
                    pu.getPurchasedAt(), null);
            p.setPurchase(pu);
            payments.save(p);
            pu.setAmountPaid(pay.amount());
        }
        pu.setPaymentStatus(Money.status(pu.getTotal(), pu.getAmountPaid()));
        audit.record(businessId, me.membershipId(), "PURCHASE_RECORDED", "Purchase", pu.getId(),
                Map.of("number", pu.getPurchaseNumber(), "lines", lines.length, "total", total));
        return get(pu.getId());
    }

    private PurchaseItem line(Purchase pu, PurchaseLineRequest l, int index, Long businessId) {
        ProductVariant variant = variants.findByIdAndBusinessId(l.variantId(), businessId)
                .orElseThrow(() -> new NotFoundException("Product variant", l.variantId()));
        Product product = variant.getProduct();
        Warehouse warehouse = stock.warehouseOrDefault(l.warehouseId());
        BigDecimal gross = amount(l.quantity().multiply(l.unitCost()));
        BigDecimal discount = amount(orZero(l.discount()));
        if (discount.compareTo(gross) > 0) {
            throw new BadRequestException("Validation failed", Map.of("items[" + index + "].discount",
                    "Discount cannot be more than the line amount " + gross.toPlainString()));
        }
        BigDecimal net = gross.subtract(discount);
        BigDecimal rate = l.taxRate() != null ? l.taxRate() : orZero(product.getTaxRate());
        BigDecimal tax = Money.percent(net, rate);
        BigDecimal landed = Money.unitCost(net, l.quantity());

        StockMovement m = stock.documentMovement(MovementType.PURCHASE, variant, warehouse, l.quantity(), null,
                l.lotNumber(), l.expiryDate(), l.serials(), "Purchase " + pu.getPurchaseNumber(), null);
        receiveCost(product, l.quantity(), landed);

        PurchaseItem it = new PurchaseItem();
        it.setBusinessId(businessId);
        it.setPurchase(pu);
        it.setLineNo(index + 1);
        it.setProduct(product);
        it.setVariant(variant);
        it.setWarehouse(warehouse);
        it.setBatch(m.getBatch());
        it.setQuantity(l.quantity());
        it.setUnitCost(l.unitCost());
        it.setGrossAmount(gross);
        it.setDiscount(discount);
        it.setNetAmount(net);
        it.setTaxRate(rate);
        it.setTaxAmount(tax);
        it.setLineTotal(net.add(tax));
        it.setLandedUnitCost(landed);
        it.setMovement(m);
        return it;
    }

    /**
     * Weighted-average cost: (stock before x old average + received x unit cost) / stock after.
     * When there was no stock (or no known cost) the received cost becomes the average.
     */
    private void receiveCost(Product product, BigDecimal received, BigDecimal unitCost) {
        em.flush(); // keep changes from earlier lines of the same product
        em.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        BigDecimal after = balances.onHandForProduct(product.getId());
        BigDecimal before = after.subtract(received);
        BigDecimal old = product.costBasis();
        BigDecimal average;
        if (old == null || before.signum() <= 0) {
            average = unitCost;
        } else {
            average = before.multiply(old).add(received.multiply(unitCost)).divide(before.add(received), 4, RoundingMode.HALF_UP);
        }
        product.setAverageCost(average);
        product.setPurchasePrice(amount(unitCost));
    }

    /** Reverses the purchase's effect on the average cost; keeps the average when no stock is left. */
    private void reverseCost(Product product, BigDecimal removed, BigDecimal unitCost) {
        em.flush(); // keep changes from earlier lines of the same product
        em.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        BigDecimal after = balances.onHandForProduct(product.getId());
        BigDecimal avg = product.getAverageCost();
        if (avg == null || after.signum() <= 0) return;
        BigDecimal value = after.add(removed).multiply(avg).subtract(removed.multiply(unitCost));
        product.setAverageCost(value.signum() <= 0 ? BigDecimal.ZERO.setScale(4)
                : value.divide(after, 4, RoundingMode.HALF_UP));
    }

    /** Removes the received stock again; fails if some of it has already been sold or used. */
    @Transactional
    public PurchaseResponse cancel(Long id, CancelPurchaseRequest r) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        CurrentUser me = CurrentUser.get();
        Purchase pu = lock(id);
        if (pu.getStatus() == PurchaseStatus.CANCELLED) throw new ConflictException("This purchase is already cancelled");
        String reason = r.reason().trim();
        List<PurchaseItem> items = new ArrayList<>(pu.getItems());
        items.sort(Comparator.comparing((PurchaseItem i) -> i.getVariant().getId()).thenComparing(i -> i.getWarehouse().getId()));
        for (PurchaseItem it : items) {
            StockMovement m;
            try {
                m = stock.documentMovement(MovementType.PURCHASE_CANCEL, it.getVariant(), it.getWarehouse(),
                        it.getQuantity(), it.getBatch() != null ? it.getBatch().getId() : null, null, null,
                        it.getMovement().getSerials(), "Cancelled " + pu.getPurchaseNumber() + ": " + reason, null);
            } catch (InsufficientStockException e) {
                throw new InsufficientStockException(it.getProduct().getName(), e);
            }
            it.setCancelMovement(m);
            reverseCost(it.getProduct(), it.getQuantity(), it.getLandedUnitCost());
        }
        pu.setStatus(PurchaseStatus.CANCELLED);
        pu.setCancelReason(reason);
        pu.setCancelledAt(java.time.Instant.now());
        pu.setCancelledBy(memberships.getReferenceById(me.membershipId()));
        audit.record(me.businessId(), me.membershipId(), "PURCHASE_CANCELLED", "Purchase", pu.getId(), Map.of("reason", reason));
        return get(pu.getId());
    }

    @Transactional
    public PurchaseResponse addPayment(Long id, PaymentRequest r) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        CurrentUser me = CurrentUser.get();
        if (payments.findByBusinessIdAndClientRequestId(me.businessId(), r.clientRequestId()).isPresent()) return get(id);
        Purchase pu = lock(id);
        if (pu.getStatus() == PurchaseStatus.CANCELLED) throw new ConflictException("This purchase is cancelled");
        BigDecimal outstanding = pu.outstanding();
        if (r.amount().compareTo(outstanding) > 0) {
            throw new BadRequestException("Validation failed", Map.of("amount",
                    outstanding.signum() == 0 ? "This purchase is already fully paid" : "Only " + outstanding.toPlainString() + " is outstanding"));
        }
        Payment p = saleService.payment(PaymentKind.PURCHASE_PAYMENT, r.amount(), r.method(), r.reference(),
                SaleService.notInFuture(r.paidAt(), "paidAt", "Payment date"), r.clientRequestId());
        p.setPurchase(pu);
        payments.save(p);
        pu.setAmountPaid(pu.getAmountPaid().add(r.amount()));
        pu.setPaymentStatus(Money.status(pu.getTotal(), pu.getAmountPaid()));
        return get(pu.getId());
    }

    @Transactional(readOnly = true)
    public PurchaseResponse get(Long id) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        Purchase pu = purchases.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Purchase", id));
        List<PurchaseItemResponse> items = pu.getItems().stream().map(it -> new PurchaseItemResponse(it.getId(), it.getLineNo(),
                it.getProduct().getId(), it.getProduct().getName(), it.getVariant().getId(), it.getVariant().getName(),
                it.getVariant().getSku(), it.getProduct().getBaseUnit().getCode(), it.getWarehouse().getId(),
                it.getWarehouse().getName(), it.getBatch() != null ? it.getBatch().getLotNumber() : null,
                it.getBatch() != null ? it.getBatch().getExpiryDate() : null, it.getQuantity(), it.getUnitCost(),
                it.getGrossAmount(), it.getDiscount(), it.getNetAmount(), it.getTaxRate(), it.getTaxAmount(),
                it.getLineTotal(), it.getLandedUnitCost())).toList();
        return new PurchaseResponse(pu.getId(), pu.getPurchaseNumber(), pu.getReferenceNumber(), pu.getStatus(),
                pu.getPurchasedAt(), pu.getSupplier() != null ? pu.getSupplier().getId() : null, pu.getSupplierName(),
                pu.getSubtotal(), pu.getDiscountTotal(), pu.getTaxTotal(), pu.getTotal(), pu.getAmountPaid(),
                pu.getStatus() == PurchaseStatus.CANCELLED ? BigDecimal.ZERO : pu.outstanding(), pu.getPaymentStatus(),
                pu.getNotes(), pu.getActor().getUser().getFullName(), pu.getCancelReason(), pu.getCancelledAt(),
                pu.getCancelledBy() != null ? pu.getCancelledBy().getUser().getFullName() : null, pu.getCreatedAt(), items,
                payments.findByPurchaseIdOrderByPaidAtAscIdAsc(pu.getId()).stream().map(SaleService::toResponse).toList());
    }

    /** q matches the purchase number, the supplier's reference or the supplier name. */
    @Transactional(readOnly = true)
    public PageResponse<PurchaseSummary> list(String q, Long supplierId, PaymentStatus paymentStatus, PurchaseStatus status,
                                              LocalDate from, LocalDate to, int page, int size) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        Long businessId = CurrentUser.get().businessId();
        ZoneId zone = businessService.zone(businessId);
        String text = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Specification<Purchase> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("businessId"), businessId));
            if (text != null) {
                p.add(cb.or(cb.like(cb.lower(root.get("purchaseNumber")), text),
                        cb.like(cb.lower(cb.coalesce(root.get("referenceNumber"), "")), text),
                        cb.like(cb.lower(cb.coalesce(root.get("supplierName"), "")), text)));
            }
            if (supplierId != null) p.add(cb.equal(root.get("supplier").get("id"), supplierId));
            if (paymentStatus != null) p.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("purchasedAt"), from.atStartOfDay(zone).toInstant()));
            if (to != null) p.add(cb.lessThan(root.get("purchasedAt"), to.plusDays(1).atStartOfDay(zone).toInstant()));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(purchases.findAll(spec, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("purchasedAt"), Sort.Order.desc("id")))), pu -> {
            List<PurchaseItem> items = pu.getItems();
            String products = items.stream().map(i -> i.getProduct().getName()).distinct().limit(3).collect(Collectors.joining(", "));
            return new PurchaseSummary(pu.getId(), pu.getPurchaseNumber(), pu.getReferenceNumber(), pu.getPurchasedAt(),
                    pu.getSupplierName(), items.size(), items.stream().map(PurchaseItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add),
                    products, pu.getTotal(), pu.getAmountPaid(),
                    pu.getStatus() == PurchaseStatus.CANCELLED ? BigDecimal.ZERO : pu.outstanding(), pu.getPaymentStatus(), pu.getStatus());
        });
    }

    private Purchase lock(Long id) {
        Purchase pu = purchases.lock(id, CurrentUser.get().businessId()).orElseThrow(() -> new NotFoundException("Purchase", id));
        return purchases.findByIdAndBusinessId(pu.getId(), pu.getBusinessId()).orElseThrow();
    }
}
