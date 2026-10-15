package com.b2binventory.service;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.ProductDtos.StockTotals;
import com.b2binventory.dto.SaleDtos.*;
import com.b2binventory.entity.*;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.InsufficientStockException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.*;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.security.Permission;
import com.b2binventory.security.Permissions;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.b2binventory.service.AuthService.blankToNull;
import static com.b2binventory.service.Money.amount;
import static com.b2binventory.service.Money.orZero;

/**
 * Sales with one or more lines. Recording a sale, its stock movements, cost snapshots and first payment
 * happen in one transaction: if any line fails (for example not enough stock) nothing is saved.
 * Cancellations and returns are new records; the original sale is never rewritten except for its status
 * and running totals.
 */
@Service
public class SaleService {

    /** Clock skew allowed for sale times sent by devices. */
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final SaleReturnRepository returns;
    private final PaymentRepository payments;
    private final ProductVariantRepository variants;
    private final MembershipRepository memberships;
    private final SequenceRepository sequences;
    private final StockService stock;
    private final StockLevels stockLevels;
    private final BusinessService businessService;
    private final PartyService parties;
    private final AuditService audit;

    public SaleService(SaleRepository sales, SaleItemRepository saleItems, SaleReturnRepository returns,
                       PaymentRepository payments, ProductVariantRepository variants, MembershipRepository memberships,
                       SequenceRepository sequences, StockService stock, StockLevels stockLevels,
                       BusinessService businessService, PartyService parties, AuditService audit) {
        this.sales = sales;
        this.saleItems = saleItems;
        this.returns = returns;
        this.payments = payments;
        this.variants = variants;
        this.memberships = memberships;
        this.sequences = sequences;
        this.stock = stock;
        this.stockLevels = stockLevels;
        this.businessService = businessService;
        this.parties = parties;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ record

    @Transactional
    public SaleResponse record(SaleRequest r) {
        Permissions.require(Permission.SALE_RECORD);
        CurrentUser me = CurrentUser.get();
        Long businessId = me.businessId();

        Optional<Sale> existing = sales.findByBusinessIdAndClientRequestId(businessId, r.clientRequestId());
        if (existing.isPresent()) return detail(existing.get());

        Sale s = new Sale();
        s.setBusinessId(businessId);
        s.setSoldAt(notInFuture(r.soldAt(), "soldAt", "Sale date"));
        s.setClientRequestId(r.clientRequestId());
        s.setActor(memberships.getReferenceById(me.membershipId()));
        s.setNotes(blankToNull(r.notes()));
        applyCustomer(s, r);
        s.setInvoiceNumber(invoiceNumber(businessId, r.invoiceNumber()));

        // Lock balances in a fixed order (variant, warehouse) so concurrent sales cannot deadlock.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < r.items().size(); i++) order.add(i);
        order.sort(Comparator.comparing((Integer i) -> r.items().get(i).variantId())
                .thenComparing(i -> Objects.requireNonNullElse(r.items().get(i).warehouseId(), 0L)));

        SaleItem[] lines = new SaleItem[r.items().size()];
        for (int i : order) lines[i] = line(s, r.items().get(i), i, businessId);
        s.getItems().addAll(Arrays.asList(lines));

        BigDecimal subtotal = BigDecimal.ZERO, discounts = BigDecimal.ZERO, tax = BigDecimal.ZERO, total = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        boolean costKnown = true;
        for (SaleItem it : lines) {
            subtotal = subtotal.add(it.getGrossAmount());
            discounts = discounts.add(it.getDiscount());
            tax = tax.add(it.getTaxAmount());
            total = total.add(it.getLineTotal());
            if (it.getCostAmount() == null) costKnown = false;
            else cost = cost.add(it.getCostAmount());
        }
        s.setSubtotal(subtotal);
        s.setDiscountTotal(discounts);
        s.setTaxTotal(tax);
        s.setTotal(total);
        s.setCostTotal(costKnown ? cost : null);
        sales.save(s);

        PaymentInput pay = r.payment();
        if (pay != null && pay.amount().signum() > 0) {
            if (pay.amount().compareTo(total) > 0) {
                throw new BadRequestException("Validation failed", Map.of("payment.amount",
                        "Amount received cannot be more than the sale total " + total.toPlainString()));
            }
            Payment p = payment(PaymentKind.SALE_PAYMENT, pay.amount(), pay.method(), pay.reference(), s.getSoldAt(), null);
            p.setSale(s);
            payments.save(p);
            s.setAmountPaid(pay.amount());
        }
        s.setPaymentStatus(Money.status(s.getTotal().subtract(s.getReturnedTotal()), s.getAmountPaid()));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("invoice", s.getInvoiceNumber());
        details.put("lines", lines.length);
        details.put("total", total);
        audit.record(businessId, me.membershipId(), "SALE_RECORDED", "Sale", s.getId(), details);
        return detail(s);
    }

    private SaleItem line(Sale s, SaleLineRequest l, int index, Long businessId) {
        String field = "items[" + index + "]";
        ProductVariant variant = variants.findByIdAndBusinessId(l.variantId(), businessId)
                .orElseThrow(() -> new NotFoundException("Product variant", l.variantId()));
        Product product = variant.getProduct();
        Warehouse warehouse = stock.warehouseOrDefault(l.warehouseId());

        BigDecimal gross = amount(l.quantity().multiply(l.unitPrice()));
        BigDecimal discount = amount(orZero(l.discount()));
        if (discount.compareTo(gross) > 0) {
            throw new BadRequestException("Validation failed", Map.of(field + ".discount",
                    "Discount on " + product.getName() + " cannot be more than the line amount " + gross.toPlainString()));
        }
        BigDecimal net = gross.subtract(discount);
        BigDecimal rate = l.taxRate() != null ? l.taxRate() : orZero(product.getTaxRate());
        BigDecimal tax = Money.percent(net, rate);
        BigDecimal unitCost = product.costBasis();

        StockMovement movement;
        try {
            movement = stock.documentMovement(MovementType.SALE, variant, warehouse, l.quantity(), l.batchId(),
                    l.lotNumber(), null, l.serials(), "Sale " + s.getInvoiceNumber(), null);
        } catch (InsufficientStockException e) {
            throw new InsufficientStockException(product.getName(), e);
        }

        SaleItem it = new SaleItem();
        it.setBusinessId(businessId);
        it.setSale(s);
        it.setLineNo(index + 1);
        it.setProduct(product);
        it.setVariant(variant);
        it.setWarehouse(warehouse);
        it.setBatch(movement.getBatch());
        it.setQuantity(l.quantity());
        it.setUnitPrice(l.unitPrice());
        it.setGrossAmount(gross);
        it.setDiscount(discount);
        it.setNetAmount(net);
        it.setTaxRate(rate);
        it.setTaxAmount(tax);
        it.setLineTotal(net.add(tax));
        it.setUnitCost(unitCost);
        it.setCostAmount(unitCost == null ? null : amount(l.quantity().multiply(unitCost)));
        it.setSerials(movement.getSerials());
        it.setMovement(movement);
        return it;
    }

    private void applyCustomer(Sale s, SaleRequest r) {
        String name = blankToNull(r.customerName());
        String phone = blankToNull(r.customerPhone());
        Customer customer = null;
        if (r.customerId() != null) {
            customer = parties.customer(r.customerId());
            if (name == null) name = customer.getName();
            if (phone == null) phone = customer.getPhone();
        } else if (r.saveCustomer() && name != null) {
            customer = parties.quickCustomer(name, phone);
        }
        s.setCustomer(customer);
        s.setCustomerName(name);
        s.setCustomerPhone(phone);
    }

    private String invoiceNumber(Long businessId, String requested) {
        String wanted = blankToNull(requested);
        if (wanted != null) {
            if (sales.existsByBusinessIdAndInvoiceNumberIgnoreCase(businessId, wanted)) {
                throw new BadRequestException("Validation failed", Map.of("invoiceNumber", "This invoice number is already used"));
            }
            return wanted;
        }
        // Skip numbers already taken (for example typed by hand or carried over from older sales).
        while (true) {
            String candidate = String.format("INV-%05d", sequences.next(businessId, "INVOICE"));
            if (!sales.existsByBusinessIdAndInvoiceNumberIgnoreCase(businessId, candidate)) return candidate;
        }
    }

    // ------------------------------------------------------------------ payments

    @Transactional
    public SaleResponse addPayment(Long saleId, PaymentRequest r) {
        Permissions.require(Permission.SALE_RECORD);
        CurrentUser me = CurrentUser.get();
        Optional<Payment> existing = payments.findByBusinessIdAndClientRequestId(me.businessId(), r.clientRequestId());
        if (existing.isPresent()) return get(saleId);

        Sale s = lock(saleId);
        if (s.getStatus() == SaleStatus.CANCELLED) throw new ConflictException("This sale is cancelled");
        BigDecimal outstanding = s.outstanding();
        if (r.amount().compareTo(outstanding) > 0) {
            throw new BadRequestException("Validation failed", Map.of("amount",
                    outstanding.signum() == 0 ? "This sale is already fully paid" : "Only " + outstanding.toPlainString() + " is outstanding"));
        }
        Payment p = payment(PaymentKind.SALE_PAYMENT, r.amount(), r.method(), r.reference(),
                notInFuture(r.paidAt(), "paidAt", "Payment date"), r.clientRequestId());
        p.setSale(s);
        payments.save(p);
        s.setAmountPaid(s.getAmountPaid().add(r.amount()));
        s.setPaymentStatus(Money.status(s.getTotal().subtract(s.getReturnedTotal()), s.getAmountPaid()));
        audit.record(me.businessId(), me.membershipId(), "SALE_PAYMENT", "Sale", s.getId(),
                Map.of("amount", r.amount(), "method", r.method()));
        return detail(s);
    }

    // ------------------------------------------------------------------ cancel

    /** Puts back all stock that has not been returned already and refunds the money received. Runs once. */
    @Transactional
    public SaleResponse cancel(Long id, CancelSaleRequest r) {
        Permissions.require(Permission.SALE_CANCEL);
        CurrentUser me = CurrentUser.get();
        Sale s = lock(id);
        if (s.getStatus() == SaleStatus.CANCELLED) throw new ConflictException("This sale is already cancelled");
        String reason = r.reason().trim();

        Map<Long, Set<String>> returnedSerials = returnedSerials(s.getId());
        for (SaleItem it : items(s)) {
            BigDecimal remaining = it.returnableQuantity();
            if (remaining.signum() <= 0) continue;
            List<String> serials = null;
            if (it.getSerials() != null) {
                Set<String> back = returnedSerials.getOrDefault(it.getId(), Set.of());
                serials = it.getSerials().stream().filter(x -> !back.contains(x)).toList();
            }
            StockMovement m = stock.documentMovement(MovementType.SALE_CANCEL, it.getVariant(), it.getWarehouse(),
                    remaining, it.getBatch() != null ? it.getBatch().getId() : null, null, null, serials,
                    "Cancelled " + s.getInvoiceNumber() + ": " + reason, null);
            it.setCancelMovement(m);
        }

        if (s.getAmountPaid().signum() > 0) {
            Payment refund = payment(PaymentKind.REFUND, s.getAmountPaid(),
                    Objects.requireNonNullElse(blankToNull(r.refundMethod()), "CASH"), "Sale cancelled", Instant.now(), null);
            refund.setSale(s);
            payments.save(refund);
            s.setAmountPaid(BigDecimal.ZERO);
        }
        s.setStatus(SaleStatus.CANCELLED);
        s.setCancelReason(reason);
        s.setCancelledAt(Instant.now());
        s.setCancelledBy(memberships.getReferenceById(me.membershipId()));
        audit.record(me.businessId(), me.membershipId(), "SALE_CANCELLED", "Sale", s.getId(), Map.of("reason", reason));
        return detail(s);
    }

    // ------------------------------------------------------------------ returns

    @Transactional
    public SaleResponse recordReturn(Long saleId, ReturnRequest r) {
        Permissions.require(Permission.SALE_CANCEL);
        CurrentUser me = CurrentUser.get();
        Long businessId = me.businessId();
        if (returns.findByBusinessIdAndClientRequestId(businessId, r.clientRequestId()).isPresent()) return get(saleId);

        Sale s = lock(saleId);
        if (s.getStatus() == SaleStatus.CANCELLED) throw new ConflictException("This sale is cancelled");
        Map<Long, SaleItem> byId = items(s).stream().collect(Collectors.toMap(SaleItem::getId, x -> x));
        Map<Long, BigDecimal[]> prior = priorReturnAmounts(s.getId());

        SaleReturn ret = new SaleReturn();
        ret.setBusinessId(businessId);
        ret.setSale(s);
        ret.setReturnNumber(String.format("RET-%05d", sequences.next(businessId, "RETURN")));
        ret.setReturnedAt(Instant.now());
        ret.setReason(r.reason().trim());
        ret.setNotes(blankToNull(r.notes()));
        ret.setActor(memberships.getReferenceById(me.membershipId()));
        ret.setClientRequestId(r.clientRequestId());

        BigDecimal credit = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < r.items().size(); i++) {
            ReturnLineRequest l = r.items().get(i);
            String field = "items[" + i + "].quantity";
            SaleItem it = byId.get(l.saleItemId());
            if (it == null) throw new BadRequestException("Validation failed", Map.of(field, "This item is not part of the sale"));
            if (!seen.add(it.getId())) throw new BadRequestException("Validation failed", Map.of(field, "Each item can be listed once"));
            BigDecimal q = l.quantity();
            BigDecimal returnable = it.returnableQuantity();
            if (q.compareTo(returnable) > 0) {
                throw new BadRequestException("Validation failed", Map.of(field, "Only "
                        + returnable.stripTrailingZeros().toPlainString() + " of " + it.getProduct().getName() + " can be returned"));
            }
            int decimals = it.getProduct().getBaseUnit().getDecimals();
            if (q.stripTrailingZeros().scale() > decimals) {
                throw new BadRequestException("Validation failed", Map.of(field, "Too many decimal places for this unit"));
            }

            // The last return of a line takes whatever is left, so returns always add up to the line exactly.
            BigDecimal[] p = prior.getOrDefault(it.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            boolean last = q.compareTo(returnable) == 0;
            BigDecimal net = last ? it.getNetAmount().subtract(p[0]) : amount(it.getNetAmount().multiply(q).divide(it.getQuantity(), 6, java.math.RoundingMode.HALF_UP));
            BigDecimal tax = last ? it.getTaxAmount().subtract(p[1]) : amount(it.getTaxAmount().multiply(q).divide(it.getQuantity(), 6, java.math.RoundingMode.HALF_UP));
            BigDecimal cost = it.getUnitCost() == null ? null : amount(q.multiply(it.getUnitCost()));

            StockMovement m = null;
            if (l.resalable()) {
                m = stock.documentMovement(MovementType.RETURN_IN, it.getVariant(), it.getWarehouse(), q,
                        it.getBatch() != null ? it.getBatch().getId() : null, null, null, l.serials(),
                        "Return " + ret.getReturnNumber() + " (" + s.getInvoiceNumber() + ")", null);
            }
            SaleReturnItem ri = new SaleReturnItem();
            ri.setSaleReturn(ret);
            ri.setSaleItem(it);
            ri.setQuantity(q);
            ri.setResalable(l.resalable());
            ri.setNetAmount(net);
            ri.setTaxAmount(tax);
            ri.setCostAmount(cost);
            ri.setMovement(m);
            ret.getItems().add(ri);
            it.setReturnedQuantity(it.getReturnedQuantity().add(q));
            credit = credit.add(net).add(tax);
        }

        BigDecimal refund = amount(orZero(r.refundAmount()));
        BigDecimal maxRefund = credit.min(s.getAmountPaid());
        if (refund.compareTo(maxRefund) > 0) {
            throw new BadRequestException("Validation failed", Map.of("refundAmount",
                    "Refund cannot be more than " + maxRefund.toPlainString()));
        }
        ret.setCreditAmount(credit);
        ret.setRefundAmount(refund);
        ret.setRefundMethod(refund.signum() > 0 ? Objects.requireNonNullElse(blankToNull(r.refundMethod()), "CASH") : null);
        returns.save(ret);
        if (refund.signum() > 0) {
            Payment p = payment(PaymentKind.REFUND, refund, ret.getRefundMethod(), ret.getReturnNumber(), ret.getReturnedAt(), null);
            p.setSale(s);
            p.setSaleReturn(ret);
            payments.save(p);
            s.setAmountPaid(s.getAmountPaid().subtract(refund));
        }
        s.setReturnedTotal(s.getReturnedTotal().add(credit));
        s.setPaymentStatus(Money.status(s.getTotal().subtract(s.getReturnedTotal()), s.getAmountPaid()));
        audit.record(businessId, me.membershipId(), "SALE_RETURN", "Sale", s.getId(),
                Map.of("return", ret.getReturnNumber(), "credit", credit, "refund", refund));
        return detail(s);
    }

    /** saleItemId -> {net, tax} already returned. */
    private Map<Long, BigDecimal[]> priorReturnAmounts(Long saleId) {
        Map<Long, BigDecimal[]> map = new HashMap<>();
        for (SaleReturn ret : returns.findBySaleIdOrderByIdAsc(saleId)) {
            for (SaleReturnItem ri : ret.getItems()) {
                map.merge(ri.getSaleItem().getId(), new BigDecimal[]{ri.getNetAmount(), ri.getTaxAmount()},
                        (a, b) -> new BigDecimal[]{a[0].add(b[0]), a[1].add(b[1])});
            }
        }
        return map;
    }

    private Map<Long, Set<String>> returnedSerials(Long saleId) {
        Map<Long, Set<String>> map = new HashMap<>();
        for (SaleReturn ret : returns.findBySaleIdOrderByIdAsc(saleId)) {
            for (SaleReturnItem ri : ret.getItems()) {
                if (ri.getMovement() != null && ri.getMovement().getSerials() != null) {
                    map.computeIfAbsent(ri.getSaleItem().getId(), k -> new HashSet<>()).addAll(ri.getMovement().getSerials());
                }
            }
        }
        return map;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public SaleResponse get(Long id) {
        Permissions.requireAny(Permission.SALE_RECORD, Permission.SALE_CANCEL, Permission.ANALYTICS_VIEW);
        return detail(sales.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Sale", id)));
    }

    /**
     * q matches the invoice number, the sale id, or the customer's name or phone.
     * from/to are inclusive dates in the business timezone.
     */
    @Transactional(readOnly = true)
    public PageResponse<SaleSummary> list(String q, Long productId, Long customerId, PaymentStatus paymentStatus,
                                          SaleStatus status, LocalDate from, LocalDate to, int page, int size) {
        Permissions.requireAny(Permission.SALE_RECORD, Permission.SALE_CANCEL, Permission.ANALYTICS_VIEW);
        Long businessId = CurrentUser.get().businessId();
        ZoneId zone = businessService.zone(businessId);
        String text = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        Specification<Sale> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("businessId"), businessId));
            if (text != null) {
                String like = "%" + text + "%";
                List<Predicate> any = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("invoiceNumber")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("customerName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("customerPhone"), "")), like)));
                String digits = text.startsWith("#") ? text.substring(1) : text;
                if (digits.matches("\\d{1,18}")) any.add(cb.equal(root.get("id"), Long.parseLong(digits)));
                p.add(cb.or(any.toArray(Predicate[]::new)));
            }
            if (productId != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                var item = sq.from(SaleItem.class);
                sq.select(item.get("sale").get("id")).where(cb.equal(item.get("product").get("id"), productId));
                p.add(root.get("id").in(sq));
            }
            if (customerId != null) p.add(cb.equal(root.join("customer", JoinType.LEFT).get("id"), customerId));
            if (paymentStatus != null) p.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("soldAt"), from.atStartOfDay(zone).toInstant()));
            if (to != null) p.add(cb.lessThan(root.get("soldAt"), to.plusDays(1).atStartOfDay(zone).toInstant()));
            return cb.and(p.toArray(Predicate[]::new));
        };
        var result = sales.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Order.desc("soldAt"), Sort.Order.desc("id"))));
        Map<Long, List<SaleItem>> itemsBySale = saleItems.findBySaleIds(result.getContent().stream().map(Sale::getId).toList())
                .stream().collect(Collectors.groupingBy(i -> i.getSale().getId()));
        return PageResponse.of(result, s -> summary(s, itemsBySale.getOrDefault(s.getId(), List.of())));
    }

    // ------------------------------------------------------------------ mapping

    private SaleSummary summary(Sale s, List<SaleItem> items) {
        BigDecimal qty = items.stream().map(SaleItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        String products = items.stream().map(i -> i.getProduct().getName()).distinct().limit(3).collect(Collectors.joining(", "));
        long distinct = items.stream().map(i -> i.getProduct().getId()).distinct().count();
        if (distinct > 3) products += " +" + (distinct - 3) + " more";
        return new SaleSummary(s.getId(), s.getInvoiceNumber(), s.getSoldAt(), s.getCustomerName(), s.getCustomerPhone(),
                items.size(), qty, products, s.getTotal(), s.getReturnedTotal(), s.getAmountPaid(), s.outstanding(),
                s.getPaymentStatus(), s.getStatus());
    }

    private SaleResponse detail(Sale s) {
        boolean showCost = Permissions.canSeeCost();
        List<SaleItem> items = items(s);
        List<SaleReturn> rets = returns.findBySaleIdOrderByIdAsc(s.getId());

        // Returned amounts per line, for line-level profit after returns.
        Map<Long, BigDecimal> returnedNet = new HashMap<>();
        Map<Long, BigDecimal> returnedCost = new HashMap<>();
        for (SaleReturn ret : rets) {
            for (SaleReturnItem ri : ret.getItems()) {
                returnedNet.merge(ri.getSaleItem().getId(), ri.getNetAmount(), BigDecimal::add);
                if (ri.isResalable() && ri.getCostAmount() != null) {
                    returnedCost.merge(ri.getSaleItem().getId(), ri.getCostAmount(), BigDecimal::add);
                }
            }
        }

        BigDecimal netSales = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        boolean costKnown = true;
        List<SaleItemResponse> itemResponses = new ArrayList<>();
        for (SaleItem it : items) {
            BigDecimal net = it.getNetAmount().subtract(returnedNet.getOrDefault(it.getId(), BigDecimal.ZERO));
            BigDecimal cost = it.getCostAmount() == null ? null
                    : it.getCostAmount().subtract(returnedCost.getOrDefault(it.getId(), BigDecimal.ZERO));
            netSales = netSales.add(net);
            if (cost == null) costKnown = false;
            else cogs = cogs.add(cost);
            ProductVariant v = it.getVariant();
            Product p = it.getProduct();
            itemResponses.add(new SaleItemResponse(it.getId(), it.getLineNo(), p.getId(), p.getName(), v.getId(), v.getName(),
                    v.getSku(), p.getBaseUnit().getCode(), it.getWarehouse().getId(), it.getWarehouse().getName(),
                    it.getBatch() != null ? it.getBatch().getLotNumber() : null, it.getQuantity(), it.getReturnedQuantity(),
                    it.getUnitPrice(), it.getGrossAmount(), it.getDiscount(), it.getNetAmount(), it.getTaxRate(),
                    it.getTaxAmount(), it.getLineTotal(), showCost ? it.getUnitCost() : null, showCost ? it.getCostAmount() : null,
                    showCost && cost != null ? net.subtract(cost) : null, it.getSerials()));
        }
        boolean cancelled = s.getStatus() == SaleStatus.CANCELLED;
        BigDecimal profit = !showCost || !costKnown || cancelled ? null : netSales.subtract(cogs);

        List<PaymentResponse> paymentResponses = payments.findBySaleIdOrderByPaidAtAscIdAsc(s.getId()).stream()
                .map(SaleService::toResponse).toList();
        List<ReturnResponse> returnResponses = rets.stream().map(ret -> new ReturnResponse(ret.getId(), ret.getReturnNumber(),
                ret.getReturnedAt(), ret.getReason(), ret.getCreditAmount(), ret.getRefundAmount(), ret.getRefundMethod(),
                ret.getNotes(), ret.getActor().getUser().getFullName(),
                ret.getItems().stream().map(ri -> new ReturnItemResponse(ri.getSaleItem().getId(),
                        ri.getSaleItem().getProduct().getName(), ri.getQuantity(), ri.isResalable(), ri.getNetAmount(),
                        ri.getTaxAmount())).toList())).toList();

        return new SaleResponse(s.getId(), s.getInvoiceNumber(), s.getStatus(), s.getSoldAt(),
                s.getCustomer() != null ? s.getCustomer().getId() : null, s.getCustomerName(), s.getCustomerPhone(),
                s.getSubtotal(), s.getDiscountTotal(), s.getTaxTotal(), s.getTotal(), s.getReturnedTotal(), s.getAmountPaid(),
                cancelled ? BigDecimal.ZERO : s.outstanding(), s.getPaymentStatus(), cancelled ? BigDecimal.ZERO : netSales,
                showCost ? s.getCostTotal() : null, profit, s.getNotes(), s.getActor().getUser().getFullName(),
                s.getCancelReason(), s.getCancelledAt(),
                s.getCancelledBy() != null ? s.getCancelledBy().getUser().getFullName() : null, s.getCreatedAt(),
                itemResponses, paymentResponses, returnResponses, stockAfter(items));
    }

    private List<StockAfter> stockAfter(List<SaleItem> items) {
        BusinessSettings settings = businessService.settingsEntity(CurrentUser.get().businessId());
        Map<Long, Product> products = new LinkedHashMap<>();
        Map<Long, List<Long>> variantIds = new HashMap<>();
        for (SaleItem it : items) {
            products.putIfAbsent(it.getProduct().getId(), it.getProduct());
        }
        for (Product p : products.values()) {
            variantIds.put(p.getId(), variants.findByProductIdOrderByIdAsc(p.getId()).stream()
                    .filter(v -> !v.isArchived()).map(ProductVariant::getId).toList());
        }
        Map<Long, StockTotals> totals = stockLevels.byVariant(variantIds.values().stream().flatMap(List::stream).toList(), null);
        List<StockAfter> out = new ArrayList<>();
        for (Product p : products.values()) {
            BigDecimal available = variantIds.get(p.getId()).stream()
                    .map(v -> totals.getOrDefault(v, StockTotals.ZERO).available()).reduce(BigDecimal.ZERO, BigDecimal::add);
            out.add(new StockAfter(p.getId(), p.getName(), p.getBaseUnit().getCode(), available,
                    StockLevels.status(available, StockLevels.threshold(p, settings))));
        }
        return out;
    }

    static PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getKind(), p.getAmount(), p.getMethod(), p.getReference(), p.getPaidAt(),
                p.getActor().getUser().getFullName());
    }

    // ------------------------------------------------------------------ helpers

    private List<SaleItem> items(Sale s) {
        return saleItems.findBySaleIds(List.of(s.getId()));
    }

    private Sale lock(Long id) {
        Sale s = sales.lock(id, CurrentUser.get().businessId()).orElseThrow(() -> new NotFoundException("Sale", id));
        return sales.findByIdAndBusinessId(s.getId(), s.getBusinessId()).orElseThrow();
    }

    Payment payment(PaymentKind kind, BigDecimal amount, String method, String reference, Instant paidAt, UUID requestId) {
        CurrentUser me = CurrentUser.get();
        Payment p = new Payment();
        p.setBusinessId(me.businessId());
        p.setKind(kind);
        p.setAmount(amount);
        p.setMethod(method.trim().toUpperCase(Locale.ROOT));
        p.setReference(blankToNull(reference));
        p.setPaidAt(paidAt);
        p.setActor(memberships.getReferenceById(me.membershipId()));
        p.setClientRequestId(requestId);
        return p;
    }

    static Instant notInFuture(Instant at, String field, String label) {
        if (at == null) return Instant.now();
        if (at.isAfter(Instant.now().plus(FUTURE_TOLERANCE))) {
            throw new BadRequestException("Validation failed", Map.of(field, label + " cannot be in the future"));
        }
        return at;
    }
}
