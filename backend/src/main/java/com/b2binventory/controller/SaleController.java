package com.b2binventory.controller;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.SaleDtos.*;
import com.b2binventory.entity.PaymentStatus;
import com.b2binventory.entity.SaleStatus;
import com.b2binventory.service.SaleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/sales")
@Tag(name = "Sales")
public class SaleController {

    private final SaleService sales;

    public SaleController(SaleService sales) {
        this.sales = sales;
    }

    /** Records the sale, reduces stock for every line and records the first payment, all or nothing. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALE_RECORD')")
    public SaleResponse record(@Valid @RequestBody SaleRequest request) {
        return sales.record(request);
    }

    /** q: invoice number, sale id, customer name or phone. from/to: inclusive dates in the business timezone. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SALE_RECORD','SALE_CANCEL','ANALYTICS_VIEW')")
    public PageResponse<SaleSummary> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Long productId,
                                          @RequestParam(required = false) Long customerId,
                                          @RequestParam(required = false) PaymentStatus paymentStatus,
                                          @RequestParam(required = false) SaleStatus status,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "30") int size) {
        return sales.list(q, productId, customerId, paymentStatus, status, from, to, page, Math.min(Math.max(size, 1), 200));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SALE_RECORD','SALE_CANCEL','ANALYTICS_VIEW')")
    public SaleResponse get(@PathVariable Long id) {
        return sales.get(id);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('SALE_RECORD')")
    public SaleResponse addPayment(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return sales.addPayment(id, request);
    }

    /** Cancels the whole sale: unreturned stock goes back and money received is refunded. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SALE_CANCEL')")
    public SaleResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelSaleRequest request) {
        return sales.cancel(id, request);
    }

    /** Customer return of some or all items; resalable items are restocked. */
    @PostMapping("/{id}/returns")
    @PreAuthorize("hasAuthority('SALE_CANCEL')")
    public SaleResponse recordReturn(@PathVariable Long id, @Valid @RequestBody ReturnRequest request) {
        return sales.recordReturn(id, request);
    }
}
