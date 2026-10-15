package com.b2binventory.controller;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.ProductDtos.StockStatus;
import com.b2binventory.dto.ReportDtos.*;
import com.b2binventory.dto.StockDtos.MovementResponse;
import com.b2binventory.entity.MovementType;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.service.ExportService;
import com.b2binventory.service.ReportService;
import com.b2binventory.service.StockService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Dashboard & reports")
public class ReportController {

    private static final int EXPORT_LIMIT = 50_000;
    private static final List<String> INVENTORY_HEADERS = List.of("Product", "Variant", "SKU", "Barcode", "Category",
            "Unit", "On hand", "Reserved", "Damaged", "Available", "Minimum stock", "Status");
    private static final List<String> MOVEMENT_HEADERS = List.of("Date (UTC)", "Type", "Product", "Variant", "SKU",
            "Warehouse", "Batch", "Quantity", "Before", "After", "Unit", "Reason", "Notes", "Serials", "By");

    private final ReportService reports;
    private final StockService stock;
    private final ExportService exports;

    public ReportController(ReportService reports, StockService stock, ExportService exports) {
        this.reports = reports;
        this.stock = stock;
        this.exports = exports;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public DashboardResponse dashboard() {
        return reports.dashboard();
    }

    @GetMapping("/reports/inventory")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public PageResponse<InventoryRow> inventory(@RequestParam(required = false) Long warehouseId,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) Set<StockStatus> status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return reports.inventory(warehouseId, categoryId, q, status, page, Math.min(Math.max(size, 1), 500));
    }

    @GetMapping("/reports/low-stock")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public PageResponse<InventoryRow> lowStock(@RequestParam(required = false) Long warehouseId,
                                               @RequestParam(required = false) Long categoryId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return reports.inventory(warehouseId, categoryId, null,
                EnumSet.of(StockStatus.LOW_STOCK, StockStatus.OUT_OF_STOCK), page, Math.min(Math.max(size, 1), 500));
    }

    @GetMapping("/reports/movements")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public PageResponse<MovementResponse> movements(@RequestParam(required = false) Long productId,
                                                    @RequestParam(required = false) Long warehouseId,
                                                    @RequestParam(required = false) List<MovementType> types,
                                                    @RequestParam(required = false) Long actorId,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        return reports.movements(productId, warehouseId, types, actorId, start(from), end(to), page,
                Math.min(Math.max(size, 1), 500));
    }

    /** report = inventory | low-stock | movements; format = csv | xlsx. */
    @GetMapping("/reports/{report}/export")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<byte[]> export(@PathVariable String report,
                                         @RequestParam(defaultValue = "csv") String format,
                                         @RequestParam(required = false) Long warehouseId,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) Long productId,
                                         @RequestParam(required = false) List<MovementType> types,
                                         @RequestParam(required = false) Long actorId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<String> headers;
        List<List<Object>> rows = new ArrayList<>();
        switch (report) {
            case "inventory", "low-stock" -> {
                Set<StockStatus> statuses = report.equals("low-stock")
                        ? EnumSet.of(StockStatus.LOW_STOCK, StockStatus.OUT_OF_STOCK) : null;
                headers = INVENTORY_HEADERS;
                reports.inventoryRows(warehouseId, categoryId, q, statuses).stream().limit(EXPORT_LIMIT)
                        .forEach(r -> rows.add(Arrays.asList(r.productName(), r.variantName(), r.sku(), r.barcode(),
                                r.category(), r.unit(), r.onHand(), r.reserved(), r.damaged(), r.available(),
                                r.minStock(), label(r.status()))));
            }
            case "movements" -> {
                headers = MOVEMENT_HEADERS;
                stock.historyAll(productId, null, warehouseId, types, actorId, start(from), end(to), EXPORT_LIMIT)
                        .forEach(m -> rows.add(Arrays.asList(m.createdAt().toString(), m.type().name(), m.productName(),
                                m.variantName(), m.sku(), m.warehouseName(), m.lotNumber(), m.quantity(),
                                m.quantityBefore(), m.quantityAfter(), m.unitCode(), m.reason(), m.notes(),
                                m.serials() == null ? null : String.join(" ", m.serials()), m.actorName())));
            }
            default -> throw new BadRequestException("Unknown report: " + report);
        }
        String base = report + "-report-" + LocalDate.now();
        byte[] body;
        MediaType type;
        String filename;
        if ("xlsx".equalsIgnoreCase(format)) {
            body = exports.xlsx(report, headers, rows);
            type = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            filename = base + ".xlsx";
        } else if ("csv".equalsIgnoreCase(format)) {
            body = exports.csv(headers, rows);
            type = MediaType.parseMediaType("text/csv; charset=UTF-8");
            filename = base + ".csv";
        } else {
            throw new BadRequestException("Format must be csv or xlsx");
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }

    private static String label(StockStatus s) {
        return switch (s) {
            case IN_STOCK -> "In stock";
            case LOW_STOCK -> "Low stock";
            case OUT_OF_STOCK -> "Out of stock";
        };
    }

    private static Instant start(LocalDate d) {
        return d == null ? null : d.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Instant end(LocalDate d) {
        return d == null ? null : d.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
