package com.b2binventory.controller;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.PurchaseDtos.*;
import com.b2binventory.dto.SaleDtos.PaymentRequest;
import com.b2binventory.entity.PaymentStatus;
import com.b2binventory.entity.PurchaseStatus;
import com.b2binventory.service.PurchaseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/purchases")
@Tag(name = "Purchases")
@PreAuthorize("hasAuthority('PURCHASE_MANAGE')")
public class PurchaseController {

    private final PurchaseService purchases;

    public PurchaseController(PurchaseService purchases) {
        this.purchases = purchases;
    }

    /** Records goods received: adds stock, updates average cost and records any payment. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse record(@Valid @RequestBody PurchaseRequest request) {
        return purchases.record(request);
    }

    @GetMapping
    public PageResponse<PurchaseSummary> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) Long supplierId,
                                              @RequestParam(required = false) PaymentStatus paymentStatus,
                                              @RequestParam(required = false) PurchaseStatus status,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "30") int size) {
        return purchases.list(q, supplierId, paymentStatus, status, from, to, page, Math.min(Math.max(size, 1), 200));
    }

    @GetMapping("/{id}")
    public PurchaseResponse get(@PathVariable Long id) {
        return purchases.get(id);
    }

    @PostMapping("/{id}/payments")
    public PurchaseResponse addPayment(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return purchases.addPayment(id, request);
    }

    @PostMapping("/{id}/cancel")
    public PurchaseResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelPurchaseRequest request) {
        return purchases.cancel(id, request);
    }
}
