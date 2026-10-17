package com.b2binventory.controller;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.TradeDtos.*;
import com.b2binventory.entity.TradeInvoiceStatus;
import com.b2binventory.service.trade.TradeInvoiceService;
import com.b2binventory.service.trade.TradeReceivingService;
import com.b2binventory.service.trade.TradeReturnService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Trade invoices between connected businesses. The seller side needs sales permissions, the buyer side purchase
 * permissions; the services also check that the caller's business is the seller or buyer on the invoice.
 */
@RestController
@RequestMapping("/api/trade")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Business invoices")
public class TradeController {

    private final TradeInvoiceService invoices;
    private final TradeReceivingService receiving;
    private final TradeReturnService returns;

    public TradeController(TradeInvoiceService invoices, TradeReceivingService receiving, TradeReturnService returns) {
        this.invoices = invoices;
        this.receiving = receiving;
        this.returns = returns;
    }

    /** direction = SALES (invoices this business sent) or PURCHASES (invoices it received). */
    @GetMapping("/invoices")
    public PageResponse<TradeInvoiceSummary> list(@RequestParam(defaultValue = "SALES") String direction,
                                                  @RequestParam(required = false) TradeInvoiceStatus status,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "30") int size) {
        return invoices.list(direction, status, q, page, size);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public TradeInvoiceResponse create(@Valid @RequestBody TradeInvoiceRequest request) {
        return invoices.create(request);
    }

    @GetMapping("/invoices/{id}")
    public TradeInvoiceResponse get(@PathVariable Long id) {
        return invoices.get(id);
    }

    @PutMapping("/invoices/{id}")
    public TradeInvoiceResponse update(@PathVariable Long id, @Valid @RequestBody TradeInvoiceRequest request) {
        return invoices.update(id, request);
    }

    @DeleteMapping("/invoices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        invoices.deleteDraft(id);
    }

    @PostMapping("/invoices/{id}/send")
    public TradeInvoiceResponse send(@PathVariable Long id) {
        return invoices.send(id);
    }

    @PostMapping("/invoices/{id}/cancel")
    public TradeInvoiceResponse cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return invoices.cancel(id, request);
    }

    @PostMapping("/invoices/{id}/reject")
    public TradeInvoiceResponse reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return invoices.reject(id, request);
    }

    /** How each line would be received into the buyer's products. */
    @GetMapping("/invoices/{id}/preview")
    public ReceivePreview preview(@PathVariable Long id, @RequestParam(required = false) Long warehouseId) {
        return receiving.preview(id, warehouseId);
    }

    @PostMapping("/invoices/{id}/approve")
    public TradeInvoiceResponse approve(@PathVariable Long id, @Valid @RequestBody(required = false) ApproveRequest request) {
        return receiving.approve(id, request);
    }

    @PostMapping("/invoices/{id}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public TradeInvoiceResponse requestReturn(@PathVariable Long id, @Valid @RequestBody TradeReturnRequest request) {
        return returns.request(id, request);
    }

    @PostMapping("/returns/{id}/accept")
    public TradeInvoiceResponse acceptReturn(@PathVariable Long id, @RequestBody(required = false) RespondRequest request) {
        return returns.accept(id, request);
    }

    @PostMapping("/returns/{id}/reject")
    public TradeInvoiceResponse rejectReturn(@PathVariable Long id, @RequestBody(required = false) RespondRequest request) {
        return returns.reject(id, request);
    }

    @PostMapping("/returns/{id}/cancel")
    public TradeInvoiceResponse cancelReturn(@PathVariable Long id) {
        return returns.cancel(id);
    }

    @GetMapping("/mappings")
    public List<ProductMappingResponse> mappings() {
        return receiving.mappings();
    }

    @DeleteMapping("/mappings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMapping(@PathVariable Long id) {
        receiving.deleteMapping(id);
    }

    @GetMapping("/summary")
    public TradeSummary summary() {
        return invoices.summary();
    }
}
