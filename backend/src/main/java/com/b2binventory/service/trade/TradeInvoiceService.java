package com.b2binventory.service.trade;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.TradeDtos.*;
import com.b2binventory.entity.*;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.InsufficientStockException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.*;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.security.Permission;
import com.b2binventory.security.Permissions;
import com.b2binventory.service.AuditService;
import com.b2binventory.service.BusinessService;
import com.b2binventory.service.Money;
import com.b2binventory.service.StockService;
import com.b2binventory.service.notification.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static com.b2binventory.service.Money.amount;
import static com.b2binventory.service.Money.orZero;

/**
 * Trade invoices: a seller invoices a connected buyer. Sending holds (reserves) the seller's stock; the buyer
 * approves ({@link TradeReceivingService}) or rejects; the seller can cancel while it waits.
 */
@Service
public class TradeInvoiceService {

    public static final String INVOICE_RECEIVED = "TRADE_INVOICE_RECEIVED";
    public static final String INVOICE_APPROVED = "TRADE_INVOICE_APPROVED";
    public static final String INVOICE_REJECTED = "TRADE_INVOICE_REJECTED";
    public static final String INVOICE_CANCELLED = "TRADE_INVOICE_CANCELLED";

    private final TradeInvoiceRepository invoices;
    private final TradeEventRepository events;
    private final TradeReturnRepository returns;
    private final ProductVariantRepository variants;
    private final WarehouseRepository warehouses;
    private final SaleRepository sales;
    private final SequenceRepository sequences;
    private final MembershipRepository memberships;
    private final StockService stock;
    private final BusinessService businessService;
    private final NetworkService network;
    private final ActingBusiness acting;
    private final NotificationService notifications;
    private final AuditService audit;

    public TradeInvoiceService(TradeInvoiceRepository invoices, TradeEventRepository events, TradeReturnRepository returns,
                               ProductVariantRepository variants, WarehouseRepository warehouses, SaleRepository sales,
                               SequenceRepository sequences, MembershipRepository memberships, StockService stock,
                               BusinessService businessService, NetworkService network, ActingBusiness acting,
                               NotificationService notifications, AuditService audit) {
        this.invoices = invoices;
        this.events = events;
        this.returns = returns;
        this.variants = variants;
        this.warehouses = warehouses;
        this.sales = sales;
        this.sequences = sequences;
        this.memberships = memberships;
        this.stock = stock;
        this.businessService = businessService;
        this.network = network;
        this.acting = acting;
        this.notifications = notifications;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ seller: create, edit, send, cancel

    @Transactional
    public TradeInvoiceResponse create(TradeInvoiceRequest r) {
        Permissions.require(Permission.SALE_RECORD);
        CurrentUser me = CurrentUser.get();
        if (r.clientRequestId() != null) {
            Optional<TradeInvoice> existing = invoices.findBySellerBusinessIdAndClientRequestId(me.businessId(), r.clientRequestId());
            if (existing.isPresent()) return get(existing.get().getId());
        }
        validateBuyer(me.businessId(), r.buyerBusinessId());

        TradeInvoice inv = new TradeInvoice();
        inv.setSellerBusinessId(me.businessId());
        inv.setInvoiceNumber(nextNumber(me.businessId()));
        inv.setCreatedBy(me.membershipId());
        inv.setClientRequestId(r.clientRequestId());
        fill(inv, r, me.businessId());
        invoices.save(inv);
        event(inv, null, "CREATED", me, null);
        audit.record(me.businessId(), me.membershipId(), "TRADE_INVOICE_CREATED", "TradeInvoice", inv.getId(),
                Map.of("number", inv.getInvoiceNumber(), "buyerBusinessId", inv.getBuyerBusinessId(), "total", inv.getTotal()));
        if (r.send()) send(inv, me);
        return get(inv.getId());
    }

    /** Replaces a draft's buyer, lines and notes. Sent invoices cannot be edited: cancel and send a new one. */
    @Transactional
    public TradeInvoiceResponse update(Long id, TradeInvoiceRequest r) {
        Permissions.require(Permission.SALE_RECORD);
        CurrentUser me = CurrentUser.get();
        TradeInvoice inv = lockAsSeller(id, me);
        if (inv.getStatus() != TradeInvoiceStatus.DRAFT) {
            throw new ConflictException("Only drafts can be edited. Cancel this invoice and send a new one instead.");
        }
        validateBuyer(me.businessId(), r.buyerBusinessId());
        inv.getLines().clear();
        invoices.flush();
        fill(inv, r, me.businessId());
        event(inv, null, "EDITED", me, null);
        if (r.send()) send(inv, me);
        return get(inv.getId());
    }

    @Transactional
    public TradeInvoiceResponse send(Long id) {
        Permissions.require(Permission.SALE_RECORD);
        CurrentUser me = CurrentUser.get();
        TradeInvoice inv = lockAsSeller(id, me);
        if (inv.getStatus() == TradeInvoiceStatus.PENDING_APPROVAL) return get(id);
        if (inv.getStatus() != TradeInvoiceStatus.DRAFT) throw new ConflictException("This invoice can no longer be sent");
        send(inv, me);
        return get(id);
    }

    @Transactional
    public void deleteDraft(Long id) {
        Permissions.require(Permission.SALE_RECORD);
        CurrentUser me = CurrentUser.get();
        TradeInvoice inv = lockAsSeller(id, me);
        if (inv.getStatus() != TradeInvoiceStatus.DRAFT) throw new ConflictException("Only drafts can be deleted");
        audit.record(me.businessId(), me.membershipId(), "TRADE_INVOICE_DELETED", "TradeInvoice", inv.getId(),
                Map.of("number", inv.getInvoiceNumber()));
        events.deleteAll(events.findByInvoiceIdOrderByCreatedAtAscIdAsc(inv.getId()));
        invoices.delete(inv);
    }

    /** The seller withdraws an invoice the buyer has not answered yet; the held stock is released. */
    @Transactional
    public TradeInvoiceResponse cancel(Long id, ReasonRequest r) {
        Permissions.require(Permission.SALE_RECORD);
        CurrentUser me = CurrentUser.get();
        TradeInvoice inv = lockAsSeller(id, me);
        if (inv.getStatus() == TradeInvoiceStatus.CANCELLED) return get(id);
        if (inv.getStatus() != TradeInvoiceStatus.DRAFT && inv.getStatus() != TradeInvoiceStatus.PENDING_APPROVAL) {
            throw new ConflictException(inv.getStatus() == TradeInvoiceStatus.POSTED
                    ? "This invoice is already approved. Use a return to send goods back." : "This invoice is already closed");
        }
        boolean wasSent = inv.getStatus() == TradeInvoiceStatus.PENDING_APPROVAL;
        if (wasSent) releaseSellerStock(inv, "Invoice " + inv.getInvoiceNumber() + " cancelled");
        inv.setStatus(TradeInvoiceStatus.CANCELLED);
        inv.setCancelReason(r.reason().trim());
        inv.setRespondedAt(Instant.now());
        event(inv, null, "CANCELLED", me, r.reason().trim());
        if (wasSent) {
            Business seller = network.business(inv.getSellerBusinessId());
            notifications.notify(inv.getBuyerBusinessId(), null, INVOICE_CANCELLED,
                    seller.getName() + " cancelled invoice " + inv.getInvoiceNumber(), "Reason: " + r.reason().trim(),
                    Map.of("tradeInvoiceId", inv.getId()), "TI:" + inv.getId() + ":CANCELLED", Duration.ofDays(365));
        }
        audit.record(me.businessId(), me.membershipId(), "TRADE_INVOICE_CANCELLED", "TradeInvoice", inv.getId(),
                Map.of("reason", r.reason().trim()));
        return get(id);
    }

    // ------------------------------------------------------------------ buyer: reject

    @Transactional
    public TradeInvoiceResponse reject(Long id, ReasonRequest r) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        CurrentUser me = CurrentUser.get();
        TradeInvoice inv = lockAsBuyer(id, me);
        if (inv.getStatus() == TradeInvoiceStatus.REJECTED) return get(id);
        if (inv.getStatus() != TradeInvoiceStatus.PENDING_APPROVAL) throw new ConflictException(closedMessage(inv));
        Long seller = acting.actor(inv.getSellerBusinessId(), inv.getCreatedBy(), Permission.SALE_RECORD);
        acting.runAs(seller, () -> releaseSellerStock(inv, "Invoice " + inv.getInvoiceNumber() + " rejected by buyer"));
        inv.setStatus(TradeInvoiceStatus.REJECTED);
        inv.setRejectionReason(r.reason().trim());
        inv.setRespondedBy(me.membershipId());
        inv.setRespondedAt(Instant.now());
        event(inv, null, "REJECTED", me, r.reason().trim());
        Business buyer = network.business(me.businessId());
        notifications.notify(inv.getSellerBusinessId(), null, INVOICE_REJECTED,
                buyer.getName() + " rejected invoice " + inv.getInvoiceNumber(), "Reason: " + r.reason().trim(),
                Map.of("tradeInvoiceId", inv.getId()), "TI:" + inv.getId() + ":REJECTED", Duration.ofDays(365));
        audit.record(me.businessId(), me.membershipId(), "TRADE_INVOICE_REJECTED", "TradeInvoice", inv.getId(),
                Map.of("reason", r.reason().trim()));
        return get(id);
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<TradeInvoiceSummary> list(String direction, TradeInvoiceStatus status, String q, int page, int size) {
        CurrentUser me = CurrentUser.get();
        boolean sales = !"PURCHASES".equalsIgnoreCase(direction);
        Permissions.require(sales ? Permission.SALE_RECORD : Permission.PURCHASE_MANAGE);
        Set<TradeInvoiceStatus> statuses = status != null ? EnumSet.of(status)
                : sales ? EnumSet.allOf(TradeInvoiceStatus.class) : EnumSet.complementOf(EnumSet.of(TradeInvoiceStatus.DRAFT));
        if (!sales) statuses.remove(TradeInvoiceStatus.DRAFT);
        String query = NetworkService.blankToNull(q);
        List<Long> matching = query == null ? List.of(-1L) : matchingBusinesses(query);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<TradeInvoice> result = sales
                ? invoices.sales(me.businessId(), statuses, query, matching, pageable)
                : invoices.purchases(me.businessId(), statuses, query, matching, pageable);
        Map<Long, Business> parties = network.businessesById(result.getContent().stream()
                .map(i -> sales ? i.getBuyerBusinessId() : i.getSellerBusinessId()).toList());
        Map<Long, String> currency = new HashMap<>();
        List<TradeInvoiceSummary> content = result.getContent().stream().map(i -> {
            Long other = sales ? i.getBuyerBusinessId() : i.getSellerBusinessId();
            boolean returnPending = i.getStatus() == TradeInvoiceStatus.POSTED && returns.findByInvoiceIdOrderByRequestedAtAsc(i.getId())
                    .stream().anyMatch(x -> x.getStatus() == TradeReturnStatus.REQUESTED);
            return new TradeInvoiceSummary(i.getId(), i.getInvoiceNumber(), i.getStatus(), sales ? "SELLER" : "BUYER",
                    NetworkService.card(parties.get(other)), i.getInvoiceDate(), i.getLines().size(), i.getTotal(),
                    currency.computeIfAbsent(i.getSellerBusinessId(), this::currency), i.getCreatedAt(), i.getSentAt(),
                    i.getRespondedAt(), returnPending);
        }).toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TradeInvoiceResponse get(Long id) {
        CurrentUser me = CurrentUser.get();
        TradeInvoice inv = invoices.findVisible(id, me.businessId()).orElseThrow(() -> new NotFoundException("Invoice", id));
        boolean seller = inv.getSellerBusinessId().equals(me.businessId());
        Permissions.require(seller ? Permission.SALE_RECORD : Permission.PURCHASE_MANAGE);
        Business s = network.business(inv.getSellerBusinessId());
        Business b = network.business(inv.getBuyerBusinessId());
        String warehouseName = (seller ? Optional.of(inv.getSellerWarehouseId()) : Optional.ofNullable(inv.getBuyerWarehouseId()))
                .flatMap(warehouses::findById).map(Warehouse::getName).orElse(null);
        List<TradeLineResponse> lines = inv.getLines().stream().map(l -> line(l, seller,
                seller || l.getBuyerVariantId() == null ? null : variants.findByIdAndBusinessId(l.getBuyerVariantId(), me.businessId())
                        .map(v -> v.getProduct().getId()).orElse(null))).toList();
        List<TradeReturnResponse> rets = returns.findByInvoiceIdOrderByRequestedAtAsc(inv.getId()).stream()
                .map(TradeInvoiceService::returnResponse).toList();
        Map<Long, String> names = Map.of(s.getId(), s.getName(), b.getId(), b.getName());
        List<TradeEventResponse> history = events.findByInvoiceIdOrderByCreatedAtAscIdAsc(inv.getId()).stream()
                .map(e -> new TradeEventResponse(e.getAction(), e.getBusinessId(), names.get(e.getBusinessId()),
                        // Staff names are shown only to their own business.
                        e.getBusinessId().equals(me.businessId()) && e.getMembershipId() != null
                                ? memberships.findWithUserById(e.getMembershipId()).map(m -> m.getUser().getFullName()).orElse(null)
                                : null,
                        e.getNote(), e.getCreatedAt()))
                .toList();
        return new TradeInvoiceResponse(inv.getId(), inv.getInvoiceNumber(), inv.getStatus(), seller ? "SELLER" : "BUYER",
                NetworkService.party(s), NetworkService.party(b), inv.getInvoiceDate(), inv.getNotes(), inv.getDeliveryNotes(),
                warehouseName, lines, inv.getSubtotal(), inv.getDiscountTotal(), inv.getTaxTotal(), inv.getTotal(),
                currency(inv.getSellerBusinessId()), inv.getCreatedAt(), inv.getSentAt(), inv.getRespondedAt(),
                inv.getRejectionReason(), inv.getCancelReason(), seller ? inv.getSellerSaleId() : null,
                seller ? null : inv.getBuyerPurchaseId(), rets, history);
    }

    @Transactional(readOnly = true)
    public TradeSummary summary() {
        CurrentUser me = CurrentUser.get();
        Long bid = me.businessId();
        boolean buyer = me.has(Permission.PURCHASE_MANAGE);
        boolean seller = me.has(Permission.SALE_RECORD);
        return new TradeSummary(
                buyer ? invoices.countByBuyerBusinessIdAndStatus(bid, TradeInvoiceStatus.PENDING_APPROVAL) : 0,
                seller ? invoices.countBySellerBusinessIdAndStatus(bid, TradeInvoiceStatus.PENDING_APPROVAL) : 0,
                seller ? invoices.countBySellerBusinessIdAndStatus(bid, TradeInvoiceStatus.DRAFT) : 0,
                me.has(Permission.BUSINESS_SETTINGS) ? network.incomingCount(bid) : 0,
                me.has(Permission.SALE_CANCEL) ? returns.countForSeller(bid, TradeReturnStatus.REQUESTED) : 0,
                network.connectedCount(bid));
    }

    // ------------------------------------------------------------------ shared with receiving and returns

    TradeInvoice lockAsSeller(Long id, CurrentUser me) {
        TradeInvoice inv = invoices.lock(id).orElseThrow(() -> new NotFoundException("Invoice", id));
        if (!inv.getSellerBusinessId().equals(me.businessId())) {
            // A buyer asking about a draft, or a stranger: do not reveal that the invoice exists.
            throw new NotFoundException("Invoice", id);
        }
        return inv;
    }

    TradeInvoice lockAsBuyer(Long id, CurrentUser me) {
        TradeInvoice inv = invoices.lock(id).orElseThrow(() -> new NotFoundException("Invoice", id));
        if (!inv.getBuyerBusinessId().equals(me.businessId()) || inv.getStatus() == TradeInvoiceStatus.DRAFT) {
            throw new NotFoundException("Invoice", id);
        }
        return inv;
    }

    void event(TradeInvoice inv, Long returnId, String action, CurrentUser me, String note) {
        TradeEvent e = new TradeEvent();
        e.setInvoiceId(inv.getId());
        e.setReturnId(returnId);
        e.setAction(action);
        e.setBusinessId(me.businessId());
        e.setMembershipId(me.membershipId());
        e.setNote(note);
        events.save(e);
    }

    String currency(Long businessId) {
        return businessService.settingsEntity(businessId).getCurrencyCode();
    }

    static String closedMessage(TradeInvoice inv) {
        return switch (inv.getStatus()) {
            case POSTED -> "This invoice has already been approved";
            case REJECTED -> "This invoice has already been rejected";
            case CANCELLED -> "The seller has cancelled this invoice";
            default -> "This invoice is not waiting for approval";
        };
    }

    static TradeReturnResponse returnResponse(TradeReturn r) {
        List<TradeReturnLineResponse> lines = r.getLines().stream().map(l -> new TradeReturnLineResponse(
                l.getInvoiceLine().getId(), l.getInvoiceLine().getProductName(), l.getInvoiceLine().getVariantName(),
                l.getInvoiceLine().getUnitCode(), l.getQuantity(), l.getAmount())).toList();
        return new TradeReturnResponse(r.getId(), r.getReturnNumber(), r.getStatus(), r.getReason(), r.getNotes(),
                r.getTotal(), r.getRequestedAt(), r.getRespondedAt(), r.getResponseNote(), lines);
    }

    // ------------------------------------------------------------------ internals

    private void send(TradeInvoice inv, CurrentUser me) {
        network.requireConnected(inv.getSellerBusinessId(), inv.getBuyerBusinessId());
        Warehouse warehouse = warehouses.findByIdAndBusinessId(inv.getSellerWarehouseId(), me.businessId())
                .orElseThrow(() -> new NotFoundException("Warehouse", inv.getSellerWarehouseId()));
        // Hold the stock in a fixed order so two invoices for the same products cannot deadlock.
        List<TradeInvoiceLine> ordered = new ArrayList<>(inv.getLines());
        ordered.sort(Comparator.comparing(TradeInvoiceLine::getSellerVariantId));
        for (TradeInvoiceLine l : ordered) {
            ProductVariant v = sellerVariant(l.getSellerVariantId(), me.businessId());
            try {
                stock.documentReserve(v, warehouse, l.getQuantity(), "Held for invoice " + inv.getInvoiceNumber());
            } catch (InsufficientStockException e) {
                throw new InsufficientStockException(l.getProductName(), e);
            }
        }
        inv.setStatus(TradeInvoiceStatus.PENDING_APPROVAL);
        inv.setSentAt(Instant.now());
        event(inv, null, "SENT", me, null);

        Business seller = network.business(inv.getSellerBusinessId());
        String money = currency(inv.getSellerBusinessId()) + " " + inv.getTotal().toPlainString();
        int n = inv.getLines().size();
        notifications.notify(inv.getBuyerBusinessId(), null, INVOICE_RECEIVED, "New purchase invoice from " + seller.getName(),
                "Invoice " + inv.getInvoiceNumber() + " · " + n + (n == 1 ? " item" : " items") + " · " + money
                        + " · Awaiting your approval",
                Map.of("tradeInvoiceId", inv.getId(), "invoiceNumber", inv.getInvoiceNumber()),
                "TI:" + inv.getId() + ":SENT", Duration.ofDays(365));
        audit.record(me.businessId(), me.membershipId(), "TRADE_INVOICE_SENT", "TradeInvoice", inv.getId(),
                Map.of("number", inv.getInvoiceNumber()));
    }

    /** Releases the seller's held stock; runs as a member of the seller business. */
    void releaseSellerStock(TradeInvoice inv, String reason) {
        Long sellerBusiness = CurrentUser.get().businessId();
        if (!sellerBusiness.equals(inv.getSellerBusinessId())) throw new IllegalStateException("Not acting as the seller");
        Warehouse warehouse = warehouses.findByIdAndBusinessId(inv.getSellerWarehouseId(), sellerBusiness).orElseThrow();
        List<TradeInvoiceLine> ordered = new ArrayList<>(inv.getLines());
        ordered.sort(Comparator.comparing(TradeInvoiceLine::getSellerVariantId));
        for (TradeInvoiceLine l : ordered) {
            ProductVariant v = variants.findByIdAndBusinessId(l.getSellerVariantId(), sellerBusiness).orElseThrow();
            stock.documentRelease(v, warehouse, l.getQuantity(), reason);
        }
    }

    private void validateBuyer(Long seller, Long buyer) {
        if (buyer.equals(seller)) throw new BadRequestException("You cannot send an invoice to your own business");
        network.business(buyer);
        network.requireConnected(seller, buyer);
    }

    /** Copies the seller's product details into the invoice and works out every amount on the server. */
    private void fill(TradeInvoice inv, TradeInvoiceRequest r, Long businessId) {
        BusinessSettings settings = businessService.settingsEntity(businessId);
        inv.setBuyerBusinessId(r.buyerBusinessId());
        Warehouse warehouse = r.warehouseId() != null
                ? warehouses.findByIdAndBusinessId(r.warehouseId(), businessId)
                    .filter(w -> !w.isArchived()).orElseThrow(() -> new NotFoundException("Warehouse", r.warehouseId()))
                : warehouses.findFirstByBusinessIdAndDefaultWarehouseTrue(businessId)
                    .or(() -> warehouses.findByBusinessIdAndArchivedFalseOrderByNameAsc(businessId).stream().findFirst())
                    .orElseThrow(() -> new BadRequestException("Create a warehouse first"));
        inv.setSellerWarehouseId(warehouse.getId());
        LocalDate date = r.invoiceDate() != null ? r.invoiceDate() : LocalDate.now(ZoneOffset.UTC);
        if (date.isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(1))) {
            throw new BadRequestException("Validation failed", Map.of("invoiceDate", "Invoice date cannot be in the future"));
        }
        inv.setInvoiceDate(date);
        inv.setNotes(NetworkService.blankToNull(r.notes()));
        inv.setDeliveryNotes(NetworkService.blankToNull(r.deliveryNotes()));

        BigDecimal subtotal = BigDecimal.ZERO, discounts = BigDecimal.ZERO, tax = BigDecimal.ZERO, total = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < r.lines().size(); i++) {
            TradeLineRequest req = r.lines().get(i);
            String field = "lines[" + i + "]";
            ProductVariant v = sellerVariant(req.variantId(), businessId);
            Product p = v.getProduct();
            if (!seen.add(v.getId())) {
                throw new BadRequestException("Validation failed", Map.of(field + ".variantId",
                        p.getName() + " is listed twice. Put the whole quantity on one line."));
            }
            if (v.isArchived() || p.isArchived()) {
                throw new BadRequestException("Validation failed", Map.of(field + ".variantId", p.getName() + " is archived"));
            }
            if ((settings.isTrackSerials() && p.isTrackSerials()) || (settings.isTrackBatches() && p.isTrackBatches())) {
                throw new BadRequestException("Validation failed", Map.of(field + ".variantId", p.getName()
                        + " is tracked by serial number or batch. Such products cannot be sent on business invoices yet."));
            }
            Unit unit = p.getBaseUnit();
            if (req.quantity().stripTrailingZeros().scale() > unit.getDecimals()) {
                throw new BadRequestException("Validation failed", Map.of(field + ".quantity", unit.getDecimals() == 0
                        ? p.getName() + " is counted in whole " + unit.getName() + "; enter a whole number"
                        : "Use at most " + unit.getDecimals() + " decimal places for " + unit.getName()));
            }
            BigDecimal price = req.unitPrice() != null ? req.unitPrice() : p.getSalePrice();
            if (price == null) {
                throw new BadRequestException("Validation failed", Map.of(field + ".unitPrice", "Enter a price for " + p.getName()));
            }
            BigDecimal gross = amount(req.quantity().multiply(price));
            BigDecimal discount = amount(orZero(req.discount()));
            if (discount.compareTo(gross) > 0) {
                throw new BadRequestException("Validation failed", Map.of(field + ".discount",
                        "Discount on " + p.getName() + " cannot be more than the line amount " + gross.toPlainString()));
            }
            BigDecimal net = gross.subtract(discount);
            BigDecimal rate = req.taxRate() != null ? req.taxRate() : orZero(p.getTaxRate());
            BigDecimal lineTax = Money.percent(net, rate);

            TradeInvoiceLine l = new TradeInvoiceLine();
            l.setInvoice(inv);
            l.setLineNo(i + 1);
            l.setSellerVariantId(v.getId());
            l.setProductName(p.getName());
            l.setVariantName(v.getName());
            l.setDefaultVariant(v.isDefaultVariant());
            l.setSku(v.getSku());
            l.setBarcode(v.getBarcode());
            l.setBrand(p.getBrand());
            l.setModel(p.getModel());
            l.setCategoryName(p.getCategory() != null ? p.getCategory().getName() : null);
            l.setDescription(p.getDescription());
            l.setAttributes(new LinkedHashMap<>(p.getAttributes()));
            l.setVariantAttributes(new LinkedHashMap<>(v.getAttributes()));
            l.setUnitCode(unit.getCode());
            l.setUnitName(unit.getName());
            l.setUnitDecimals(unit.getDecimals());
            l.setQuantity(req.quantity());
            l.setUnitPrice(price);
            l.setGrossAmount(gross);
            l.setDiscount(discount);
            l.setNetAmount(net);
            l.setTaxRate(rate);
            l.setTaxAmount(lineTax);
            l.setLineTotal(net.add(lineTax));
            inv.getLines().add(l);

            subtotal = subtotal.add(gross);
            discounts = discounts.add(discount);
            tax = tax.add(lineTax);
            total = total.add(l.getLineTotal());
        }
        inv.setSubtotal(subtotal);
        inv.setDiscountTotal(discounts);
        inv.setTaxTotal(tax);
        inv.setTotal(total);
    }

    private ProductVariant sellerVariant(Long id, Long businessId) {
        return variants.findByIdAndBusinessId(id, businessId).orElseThrow(() -> new NotFoundException("Product variant", id));
    }

    /**
     * Numbers look like DT-2026-00012: the seller's initials, the year and a running number. A number already used
     * by one of the seller's own sales is skipped, because the approved invoice becomes a sale with the same number.
     */
    private String nextNumber(Long sellerBusinessId) {
        Business b = network.business(sellerBusinessId);
        StringBuilder initials = new StringBuilder();
        for (String word : b.getName().trim().split("\\s+")) {
            if (initials.length() == 3) break;
            char c = word.isEmpty() ? ' ' : Character.toUpperCase(word.charAt(0));
            if (c >= 'A' && c <= 'Z') initials.append(c);
        }
        String prefix = initials.length() == 0 ? "TI" : initials.toString();
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        while (true) {
            String candidate = String.format("%s-%d-%05d", prefix, year, sequences.next(sellerBusinessId, "TRADE_INVOICE"));
            if (!sales.existsByBusinessIdAndInvoiceNumberIgnoreCase(sellerBusinessId, candidate)) return candidate;
        }
    }

    private List<Long> matchingBusinesses(String q) {
        String like = q.toLowerCase(Locale.ROOT);
        List<Long> ids = new ArrayList<>();
        for (Business b : network.searchByName(like)) ids.add(b.getId());
        if (ids.isEmpty()) ids.add(-1L);
        return ids;
    }

    private static TradeLineResponse line(TradeInvoiceLine l, boolean seller, Long buyerProductId) {
        return new TradeLineResponse(l.getId(), l.getLineNo(), l.getProductName(), l.getVariantName(), l.isDefaultVariant(),
                l.getSku(), l.getBarcode(), l.getBrand(), l.getModel(), l.getCategoryName(), l.getDescription(),
                l.getAttributes(), l.getVariantAttributes(), l.getUnitCode(), l.getUnitName(), l.getUnitDecimals(),
                l.getQuantity(), l.getUnitPrice(), l.getGrossAmount(), l.getDiscount(), l.getNetAmount(), l.getTaxRate(),
                l.getTaxAmount(), l.getLineTotal(), seller ? l.getSellerVariantId() : null,
                seller ? null : l.getBuyerVariantId(), l.getReturnedQuantity(), buyerProductId);
    }
}
