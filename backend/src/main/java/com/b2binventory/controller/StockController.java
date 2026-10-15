package com.b2binventory.controller;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.ProductDtos.BalanceResponse;
import com.b2binventory.dto.StockDtos.*;
import com.b2binventory.entity.MovementType;
import com.b2binventory.service.StockCountService;
import com.b2binventory.service.StockService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Stock")
public class StockController {

    private final StockService stock;
    private final StockCountService counts;

    public StockController(StockService stock, StockCountService counts) {
        this.stock = stock;
        this.counts = counts;
    }

    /** Fine-grained permission (add / reduce / adjust) is checked per operation type in the service. */
    @PostMapping("/stock/operations")
    @PreAuthorize("hasAnyAuthority('STOCK_ADD','STOCK_REDUCE','STOCK_ADJUST')")
    public OperationResult operate(@Valid @RequestBody StockOperationRequest request) {
        return stock.operate(request);
    }

    @PostMapping("/stock/transfers")
    @PreAuthorize("hasAuthority('STOCK_TRANSFER')")
    public OperationResult transfer(@Valid @RequestBody TransferRequest request) {
        return stock.transfer(request);
    }

    @GetMapping("/stock/movements")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public PageResponse<MovementResponse> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long variantId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) List<MovementType> types,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return stock.history(productId, variantId, warehouseId, types, actorId, from, to, page, Math.min(size, 200));
    }

    @GetMapping("/variants/{variantId}/balances")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public List<BalanceResponse> balances(@PathVariable Long variantId) {
        return stock.balancesForVariant(variantId);
    }

    @GetMapping("/variants/{variantId}/batches")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public List<BatchResponse> batches(@PathVariable Long variantId) {
        return stock.batchesForVariant(variantId);
    }

    @GetMapping("/variants/{variantId}/serials")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public List<SerialResponse> serials(@PathVariable Long variantId) {
        return stock.serialsForVariant(variantId);
    }

    // ---- Stock counts ----

    @GetMapping("/stock-counts")
    @PreAuthorize("hasAuthority('STOCK_COUNT')")
    public PageResponse<StockCountSummary> counts(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return counts.list(page, Math.min(size, 100));
    }

    @GetMapping("/stock-counts/{id}")
    @PreAuthorize("hasAuthority('STOCK_COUNT')")
    public StockCountResponse count(@PathVariable Long id) {
        return counts.get(id);
    }

    @PostMapping("/stock-counts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_COUNT')")
    public StockCountResponse createCount(@Valid @RequestBody StockCountRequest request) {
        return counts.create(request);
    }

    @PutMapping("/stock-counts/{id}/lines")
    @PreAuthorize("hasAuthority('STOCK_COUNT')")
    public StockCountResponse updateLines(@PathVariable Long id, @Valid @RequestBody CountLinesRequest request) {
        return counts.updateLines(id, request);
    }

    @PostMapping("/stock-counts/{id}/reconcile")
    @PreAuthorize("hasAuthority('STOCK_COUNT') and hasAuthority('STOCK_ADJUST')")
    public ReconcileResult reconcile(@PathVariable Long id) {
        return counts.reconcile(id);
    }

    @PostMapping("/stock-counts/{id}/cancel")
    @PreAuthorize("hasAuthority('STOCK_COUNT')")
    public StockCountSummary cancel(@PathVariable Long id) {
        return counts.cancel(id);
    }
}
