package com.b2binventory.service.trade;

import com.b2binventory.dto.SaleDtos.ReturnLineRequest;
import com.b2binventory.dto.SaleDtos.ReturnRequest;
import com.b2binventory.dto.SaleDtos.SaleItemResponse;
import com.b2binventory.dto.SaleDtos.SaleResponse;
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
import com.b2binventory.service.Money;
import com.b2binventory.service.SaleService;
import com.b2binventory.service.StockService;
import com.b2binventory.service.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Returns against a posted trade invoice. The buyer asks (their stock is held so it is not sold meanwhile); when
 * the seller accepts, in one transaction the goods leave the buyer's stock and come back into the seller's stock as
 * a return on the original sale. Rejecting or cancelling releases the buyer's hold.
 */
@Service
public class TradeReturnService {

    public static final String RETURN_REQUESTED = "TRADE_RETURN_REQUESTED";
    public static final String RETURN_ACCEPTED = "TRADE_RETURN_ACCEPTED";
    public static final String RETURN_REJECTED = "TRADE_RETURN_REJECTED";

    private final TradeInvoiceService invoiceService;
    private final TradeReturnRepository returns;
    private final ProductVariantRepository variants;
    private final WarehouseRepository warehouses;
    private final SaleReturnRepository saleReturns;
    private final StockService stock;
    private final SaleService saleService;
    private final NetworkService network;
    private final ActingBusiness acting;
    private final NotificationService notifications;
    private final AuditService audit;

    public TradeReturnService(TradeInvoiceService invoiceService, TradeReturnRepository returns,
                              ProductVariantRepository variants, WarehouseRepository warehouses,
                              SaleReturnRepository saleReturns, StockService stock, SaleService saleService,
                              NetworkService network, ActingBusiness acting, NotificationService notifications,
                              AuditService audit) {
        this.invoiceService = invoiceService;
        this.returns = returns;
        this.variants = variants;
        this.warehouses = warehouses;
        this.saleReturns = saleReturns;
        this.stock = stock;
        this.saleService = saleService;
        this.network = network;
        this.acting = acting;
        this.notifications = notifications;
        this.audit = audit;
    }

    /** The buyer asks to send goods back. */
    @Transactional
    public TradeInvoiceResponse request(Long invoiceId, TradeReturnRequest r) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        CurrentUser me = CurrentUser.get();
        TradeInvoice inv = invoiceService.lockAsBuyer(invoiceId, me);
        if (r.clientRequestId() != null && returns.findByInvoiceIdAndClientRequestId(inv.getId(), r.clientRequestId()).isPresent()) {
            return invoiceService.get(invoiceId);
        }
        if (inv.getStatus() != TradeInvoiceStatus.POSTED) throw new ConflictException("Only approved invoices can be returned");

        Map<Long, TradeInvoiceLine> byId = new HashMap<>();
        inv.getLines().forEach(l -> byId.put(l.getId(), l));
        Map<Long, BigDecimal> pending = new HashMap<>();
        for (TradeReturn other : returns.findByInvoiceIdOrderByRequestedAtAsc(inv.getId())) {
            if (other.getStatus() != TradeReturnStatus.REQUESTED) continue;
            other.getLines().forEach(x -> pending.merge(x.getInvoiceLine().getId(), x.getQuantity(), BigDecimal::add));
        }

        TradeReturn ret = new TradeReturn();
        ret.setInvoice(inv);
        ret.setReturnNumber(inv.getInvoiceNumber() + "-R" + (returns.findByInvoiceIdOrderByRequestedAtAsc(inv.getId()).size() + 1));
        ret.setReason(r.reason().trim());
        ret.setNotes(NetworkService.blankToNull(r.notes()));
        ret.setRequestedBy(me.membershipId());
        ret.setClientRequestId(r.clientRequestId());

        Warehouse warehouse = warehouses.findByIdAndBusinessId(inv.getBuyerWarehouseId(), me.businessId()).orElseThrow();
        Set<Long> seen = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < r.lines().size(); i++) {
            var req = r.lines().get(i);
            String field = "lines[" + i + "].quantity";
            TradeInvoiceLine l = byId.get(req.invoiceLineId());
            if (l == null) throw new BadRequestException("Validation failed", Map.of(field, "This item is not on the invoice"));
            if (!seen.add(l.getId())) throw new BadRequestException("Validation failed", Map.of(field, "Each item can be listed once"));
            BigDecimal returnable = l.getQuantity().subtract(l.getReturnedQuantity())
                    .subtract(pending.getOrDefault(l.getId(), BigDecimal.ZERO));
            if (req.quantity().compareTo(returnable) > 0) {
                throw new BadRequestException("Validation failed", Map.of(field, "Only " + returnable.stripTrailingZeros().toPlainString()
                        + " " + l.getUnitCode() + " of " + l.getProductName() + " can still be returned"));
            }
            if (req.quantity().stripTrailingZeros().scale() > l.getUnitDecimals()) {
                throw new BadRequestException("Validation failed", Map.of(field, "Use at most " + l.getUnitDecimals()
                        + " decimal places for " + l.getUnitName()));
            }
            TradeReturnLine rl = new TradeReturnLine();
            rl.setTradeReturn(ret);
            rl.setInvoiceLine(l);
            rl.setQuantity(req.quantity());
            // Share of the line's value (after discount, with tax) for the quantity sent back.
            rl.setAmount(Money.amount(l.getLineTotal().multiply(req.quantity()).divide(l.getQuantity(), 6, RoundingMode.HALF_UP)));
            ret.getLines().add(rl);
            total = total.add(rl.getAmount());
        }
        ret.setTotal(total);
        returns.save(ret);

        // Hold the goods in the buyer's stock so they are not sold while the seller decides.
        List<TradeReturnLine> ordered = new ArrayList<>(ret.getLines());
        ordered.sort(Comparator.comparing(x -> x.getInvoiceLine().getBuyerVariantId()));
        for (TradeReturnLine rl : ordered) {
            ProductVariant v = variants.findByIdAndBusinessId(rl.getInvoiceLine().getBuyerVariantId(), me.businessId()).orElseThrow();
            try {
                stock.documentReserve(v, warehouse, rl.getQuantity(), "Held for return " + ret.getReturnNumber());
            } catch (InsufficientStockException e) {
                throw new InsufficientStockException(rl.getInvoiceLine().getProductName(), e);
            }
        }
        invoiceService.event(inv, ret.getId(), "RETURN_REQUESTED", me, ret.getReturnNumber() + " · " + ret.getReason());
        Business buyer = network.business(me.businessId());
        notifications.notify(inv.getSellerBusinessId(), null, RETURN_REQUESTED,
                buyer.getName() + " wants to return goods", "Return " + ret.getReturnNumber() + " on invoice "
                        + inv.getInvoiceNumber() + " · Reason: " + ret.getReason(),
                Map.of("tradeInvoiceId", inv.getId(), "tradeReturnId", ret.getId()), "TR:" + ret.getId() + ":REQ",
                Duration.ofDays(365));
        audit.record(me.businessId(), me.membershipId(), "TRADE_RETURN_REQUESTED", "TradeReturn", ret.getId(),
                Map.of("number", ret.getReturnNumber(), "total", total));
        return invoiceService.get(invoiceId);
    }

    /** The seller accepts: the goods leave the buyer's stock and come back into the seller's stock. */
    @Transactional
    public TradeInvoiceResponse accept(Long returnId, RespondRequest r) {
        Permissions.require(Permission.SALE_CANCEL);
        CurrentUser me = CurrentUser.get();
        TradeReturn ret = returns.lock(returnId).orElseThrow(() -> new NotFoundException("Return", returnId));
        TradeInvoice inv = invoiceService.lockAsSeller(ret.getInvoice().getId(), me);
        if (ret.getStatus() == TradeReturnStatus.ACCEPTED) return invoiceService.get(inv.getId());
        if (ret.getStatus() != TradeReturnStatus.REQUESTED) throw new ConflictException("This return has already been answered");

        // Buyer side, as the member who asked for the return: release the hold and send the goods out.
        Long buyerActor = acting.actor(inv.getBuyerBusinessId(), ret.getRequestedBy(), Permission.PURCHASE_MANAGE);
        Business seller = network.business(inv.getSellerBusinessId());
        acting.runAs(buyerActor, () -> {
            Long buyerId = inv.getBuyerBusinessId();
            Warehouse warehouse = warehouses.findByIdAndBusinessId(inv.getBuyerWarehouseId(), buyerId).orElseThrow();
            List<TradeReturnLine> ordered = new ArrayList<>(ret.getLines());
            ordered.sort(Comparator.comparing(x -> x.getInvoiceLine().getBuyerVariantId()));
            for (TradeReturnLine rl : ordered) {
                ProductVariant v = variants.findByIdAndBusinessId(rl.getInvoiceLine().getBuyerVariantId(), buyerId).orElseThrow();
                stock.documentRelease(v, warehouse, rl.getQuantity(), "Return " + ret.getReturnNumber() + " accepted");
                stock.documentMovement(MovementType.PURCHASE_RETURN, v, warehouse, rl.getQuantity(), null, null, null, null,
                        "Returned to " + seller.getName() + " · " + ret.getReturnNumber(), null);
            }
        });

        // Seller side: a return on the original sale brings the stock back (credited, no cash refund).
        SaleResponse sale = saleService.get(inv.getSellerSaleId());
        Map<Integer, SaleItemResponse> byLineNo = new HashMap<>();
        sale.items().forEach(it -> byLineNo.put(it.lineNo(), it));
        List<ReturnLineRequest> items = new ArrayList<>();
        for (TradeReturnLine rl : ret.getLines()) {
            SaleItemResponse it = byLineNo.get(rl.getInvoiceLine().getLineNo());
            if (it == null || !it.variantId().equals(rl.getInvoiceLine().getSellerVariantId())) {
                throw new IllegalStateException("Sale line not found for return line " + rl.getId());
            }
            items.add(new ReturnLineRequest(it.id(), rl.getQuantity(), true, null));
        }
        UUID requestId = TradeReceivingService.requestId("trade-return", ret.getId());
        saleService.recordReturn(inv.getSellerSaleId(), new ReturnRequest(items, "Business return " + ret.getReturnNumber()
                + ": " + ret.getReason(), BigDecimal.ZERO, null, ret.getNotes(), requestId));
        saleReturns.findByBusinessIdAndClientRequestId(me.businessId(), requestId)
                .ifPresent(sr -> ret.setSellerSaleReturnId(sr.getId()));

        for (TradeReturnLine rl : ret.getLines()) {
            TradeInvoiceLine l = rl.getInvoiceLine();
            l.setReturnedQuantity(l.getReturnedQuantity().add(rl.getQuantity()));
        }
        ret.setStatus(TradeReturnStatus.ACCEPTED);
        ret.setRespondedBy(me.membershipId());
        ret.setRespondedAt(Instant.now());
        ret.setResponseNote(r == null ? null : NetworkService.blankToNull(r.note()));
        invoiceService.event(inv, ret.getId(), "RETURN_ACCEPTED", me, ret.getReturnNumber());
        notifications.notify(inv.getBuyerBusinessId(), null, RETURN_ACCEPTED,
                seller.getName() + " accepted return " + ret.getReturnNumber(),
                "The returned goods have been removed from your stock.",
                Map.of("tradeInvoiceId", inv.getId(), "tradeReturnId", ret.getId()), "TR:" + ret.getId() + ":OK",
                Duration.ofDays(365));
        audit.record(me.businessId(), me.membershipId(), "TRADE_RETURN_ACCEPTED", "TradeReturn", ret.getId(),
                Map.of("number", ret.getReturnNumber()));
        audit.record(inv.getBuyerBusinessId(), buyerActor, "TRADE_RETURN_POSTED", "TradeReturn", ret.getId(),
                Map.of("number", ret.getReturnNumber()));
        return invoiceService.get(inv.getId());
    }

    /** The seller refuses the return; the buyer's held stock is released. */
    @Transactional
    public TradeInvoiceResponse reject(Long returnId, RespondRequest r) {
        Permissions.require(Permission.SALE_CANCEL);
        CurrentUser me = CurrentUser.get();
        TradeReturn ret = returns.lock(returnId).orElseThrow(() -> new NotFoundException("Return", returnId));
        TradeInvoice inv = invoiceService.lockAsSeller(ret.getInvoice().getId(), me);
        if (ret.getStatus() == TradeReturnStatus.REJECTED) return invoiceService.get(inv.getId());
        if (ret.getStatus() != TradeReturnStatus.REQUESTED) throw new ConflictException("This return has already been answered");
        Long buyerActor = acting.actor(inv.getBuyerBusinessId(), ret.getRequestedBy(), Permission.PURCHASE_MANAGE);
        acting.runAs(buyerActor, () -> releaseBuyerHold(inv, ret, "Return " + ret.getReturnNumber() + " rejected"));
        close(ret, TradeReturnStatus.REJECTED, me, r);
        invoiceService.event(inv, ret.getId(), "RETURN_REJECTED", me, ret.getResponseNote());
        Business seller = network.business(inv.getSellerBusinessId());
        notifications.notify(inv.getBuyerBusinessId(), null, RETURN_REJECTED,
                seller.getName() + " did not accept return " + ret.getReturnNumber(),
                ret.getResponseNote() == null ? "The held stock is available again." : "Note: " + ret.getResponseNote(),
                Map.of("tradeInvoiceId", inv.getId(), "tradeReturnId", ret.getId()), "TR:" + ret.getId() + ":NO",
                Duration.ofDays(365));
        return invoiceService.get(inv.getId());
    }

    /** The buyer withdraws a return the seller has not answered yet. */
    @Transactional
    public TradeInvoiceResponse cancel(Long returnId) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        CurrentUser me = CurrentUser.get();
        TradeReturn ret = returns.lock(returnId).orElseThrow(() -> new NotFoundException("Return", returnId));
        TradeInvoice inv = invoiceService.lockAsBuyer(ret.getInvoice().getId(), me);
        if (ret.getStatus() == TradeReturnStatus.CANCELLED) return invoiceService.get(inv.getId());
        if (ret.getStatus() != TradeReturnStatus.REQUESTED) throw new ConflictException("This return has already been answered");
        releaseBuyerHold(inv, ret, "Return " + ret.getReturnNumber() + " withdrawn");
        close(ret, TradeReturnStatus.CANCELLED, me, null);
        invoiceService.event(inv, ret.getId(), "RETURN_CANCELLED", me, ret.getReturnNumber());
        return invoiceService.get(inv.getId());
    }

    private void releaseBuyerHold(TradeInvoice inv, TradeReturn ret, String reason) {
        Long buyerId = CurrentUser.get().businessId();
        Warehouse warehouse = warehouses.findByIdAndBusinessId(inv.getBuyerWarehouseId(), buyerId).orElseThrow();
        List<TradeReturnLine> ordered = new ArrayList<>(ret.getLines());
        ordered.sort(Comparator.comparing(x -> x.getInvoiceLine().getBuyerVariantId()));
        for (TradeReturnLine rl : ordered) {
            ProductVariant v = variants.findByIdAndBusinessId(rl.getInvoiceLine().getBuyerVariantId(), buyerId).orElseThrow();
            stock.documentRelease(v, warehouse, rl.getQuantity(), reason);
        }
    }

    private static void close(TradeReturn ret, TradeReturnStatus status, CurrentUser me, RespondRequest r) {
        ret.setStatus(status);
        ret.setRespondedBy(me.membershipId());
        ret.setRespondedAt(Instant.now());
        ret.setResponseNote(r == null ? null : NetworkService.blankToNull(r.note()));
    }
}
