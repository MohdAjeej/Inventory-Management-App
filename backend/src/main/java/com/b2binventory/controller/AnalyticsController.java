package com.b2binventory.controller;

import com.b2binventory.dto.AnalyticsDtos.*;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.service.AnalyticsService;
import com.b2binventory.service.ExportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Sales analytics. from/to are inclusive dates in the business timezone; the default is the last 30 days. */
@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics")
@PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
public class AnalyticsController {

    private static final List<String> PRODUCT_HEADERS = List.of("Rank", "Product", "SKU", "Category", "Unit",
            "Units sold", "Net sales", "Gross profit", "Margin %", "Sales", "Average daily sales", "Current stock", "Stock status",
            "Previous period units", "Growth %", "Performance", "Labels");

    private final AnalyticsService analytics;
    private final ExportService exports;

    public AnalyticsController(AnalyticsService analytics, ExportService exports) {
        this.analytics = analytics;
        this.exports = exports;
    }

    @GetMapping("/overview")
    public Overview overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.overview(from, to);
    }

    @GetMapping("/products")
    public ProductAnalytics products(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(required = false) Long categoryId,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "units") String sort) {
        return analytics.products(from, to, categoryId, q, sort);
    }

    @GetMapping("/trend")
    public Trend trend(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "day") String granularity,
                       @RequestParam(required = false) Long productId) {
        return analytics.trend(from, to, granularity, productId);
    }

    @GetMapping("/categories")
    public CategoryAnalytics categories(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.categories(from, to);
    }

    @GetMapping("/insights")
    public Insights insights(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.insights(from, to);
    }

    @GetMapping("/products/export")
    @PreAuthorize("hasAuthority('ANALYTICS_VIEW') and hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "units") String sort) {
        ProductAnalytics data = analytics.products(from, to, categoryId, q, sort);
        List<List<Object>> rows = new ArrayList<>();
        data.products().forEach(p -> rows.add(Arrays.asList(p.rank(), p.name(), p.sku(), p.categoryName(), p.unit(),
                p.unitsSold(), p.revenue(), p.grossProfit(), p.marginPercent(), p.salesCount(), p.averageDailySales(), p.currentStock(),
                p.stockStatus().name(), p.previousUnits(), p.growthPercent(), p.performance().name(),
                String.join(" ", p.labels()))));
        String base = "product-analytics-" + data.period().from() + "-to-" + data.period().to();
        if ("xlsx".equalsIgnoreCase(format)) {
            return file(exports.xlsx("Product analytics", PRODUCT_HEADERS, rows),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", base + ".xlsx");
        }
        if ("csv".equalsIgnoreCase(format)) {
            return file(exports.csv(PRODUCT_HEADERS, rows), "text/csv; charset=UTF-8", base + ".csv");
        }
        throw new BadRequestException("Format must be csv or xlsx");
    }

    private static ResponseEntity<byte[]> file(byte[] body, String type, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
