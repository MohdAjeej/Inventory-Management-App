package com.b2binventory.dto;

import com.b2binventory.dto.ProductDtos.BalanceResponse;
import com.b2binventory.entity.CountStatus;
import com.b2binventory.entity.MovementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class StockDtos {

    private StockDtos() {
    }

    /** Operations a user can request directly. OPENING is recorded at product creation. */
    public enum OperationType { ADD, REDUCE, ADJUST, DAMAGE, RESERVE, RELEASE }

    /**
     * quantity means: the amount to add/reduce/damage/reserve/release, or for ADJUST the corrected
     * on-hand quantity. warehouseId defaults to the business's default warehouse.
     */
    public record StockOperationRequest(
            @NotNull(message = "Operation type is required") OperationType type,
            @NotNull(message = "Product variant is required") Long variantId,
            Long warehouseId,
            @NotNull(message = "Quantity is required") @DecimalMin(value = "0", message = "Quantity cannot be negative") BigDecimal quantity,
            @Size(max = 200) String reason,
            @Size(max = 1000) String notes,
            Long batchId,
            @Size(max = 80) String lotNumber,
            LocalDate expiryDate,
            List<@NotBlank @Size(max = 120) String> serials,
            boolean fromDamaged,
            UUID clientRequestId) {
    }

    public record TransferRequest(
            @NotNull(message = "Product variant is required") Long variantId,
            @NotNull(message = "Source warehouse is required") Long fromWarehouseId,
            @NotNull(message = "Destination warehouse is required") Long toWarehouseId,
            @NotNull(message = "Quantity is required") @DecimalMin(value = "0", inclusive = false, message = "Quantity must be greater than zero") BigDecimal quantity,
            Long batchId,
            @Size(max = 80) String lotNumber,
            List<@NotBlank @Size(max = 120) String> serials,
            @Size(max = 200) String reason,
            @Size(max = 1000) String notes,
            UUID clientRequestId) {
    }

    public record MovementResponse(
            Long id, MovementType type, String bucket, Long productId, String productName, Long variantId,
            String variantName, String sku, Long warehouseId, String warehouseName, Long batchId, String lotNumber,
            BigDecimal quantity, BigDecimal quantityBefore, BigDecimal quantityAfter, String unitCode, String reason,
            String notes, List<String> serials, Long actorId, String actorName, UUID transferGroupId,
            Long stockCountId, Instant createdAt) {
    }

    public record OperationResult(List<MovementResponse> movements, List<BalanceResponse> balances) {
    }

    public record BatchResponse(Long id, String lotNumber, LocalDate expiryDate, BigDecimal onHand) {
    }

    public record SerialResponse(Long id, String serial, String status, Long warehouseId, String warehouseName,
                                 Instant updatedAt) {
    }

    // ---- Warehouses ----

    public record WarehouseRequest(
            @NotBlank(message = "Name is required") @Size(max = 120) String name,
            @NotBlank(message = "Code is required") @Size(max = 30) String code,
            @Size(max = 500) String address,
            boolean branch) {
    }

    public record WarehouseResponse(Long id, String name, String code, String address, boolean branch,
                                    boolean isDefault, boolean archived, long productCount, BigDecimal totalQuantity) {
    }

    public record LocationRequest(
            @NotBlank(message = "Code is required") @Size(max = 40) String code,
            @Size(max = 200) String description) {
    }

    public record LocationResponse(Long id, Long warehouseId, String code, String description, boolean archived) {
    }

    // ---- Stock counts ----

    public record StockCountRequest(
            @NotNull(message = "Warehouse is required") Long warehouseId,
            Long categoryId,
            @Size(max = 1000) String notes) {
    }

    public record CountLineUpdate(
            @NotNull Long lineId,
            @DecimalMin(value = "0", message = "Counted quantity cannot be negative") BigDecimal countedQty) {
    }

    public record CountLinesRequest(@NotNull @Valid List<CountLineUpdate> lines) {
    }

    public record CountLineResponse(Long id, Long variantId, Long productId, String productName, String variantName,
                                    String sku, String unitCode, Long batchId, String lotNumber,
                                    BigDecimal expectedQty, BigDecimal countedQty, BigDecimal difference) {
    }

    public record StockCountSummary(Long id, Long warehouseId, String warehouseName, CountStatus status, String notes,
                                    String createdBy, Instant createdAt, Instant reconciledAt, int lineCount,
                                    int countedLines) {
    }

    public record StockCountResponse(StockCountSummary summary, List<CountLineResponse> lines) {
    }

    public record ReconcileResult(StockCountSummary summary, int adjustedLines) {
    }
}
