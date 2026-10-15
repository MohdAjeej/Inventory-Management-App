package com.b2binventory.service;

import com.b2binventory.dto.StockDtos.*;
import com.b2binventory.entity.StorageLocation;
import com.b2binventory.entity.Warehouse;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.StockBalanceRepository;
import com.b2binventory.repository.StorageLocationRepository;
import com.b2binventory.repository.WarehouseRepository;
import com.b2binventory.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouses;
    private final StorageLocationRepository locations;
    private final StockBalanceRepository balances;
    private final AuditService audit;

    public WarehouseService(WarehouseRepository warehouses, StorageLocationRepository locations,
                            StockBalanceRepository balances, AuditService audit) {
        this.warehouses = warehouses;
        this.locations = locations;
        this.balances = balances;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> list(boolean includeArchived) {
        Long businessId = CurrentUser.get().businessId();
        Map<Long, Object[]> totals = new HashMap<>();
        balances.warehouseTotals(businessId).forEach(r -> totals.put((Long) r[0], r));
        return (includeArchived ? warehouses.findByBusinessIdOrderByNameAsc(businessId)
                : warehouses.findByBusinessIdAndArchivedFalseOrderByNameAsc(businessId))
                .stream().map(w -> toResponse(w, totals.get(w.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(Long id) {
        Warehouse w = find(id);
        Object[] totals = balances.warehouseTotals(w.getBusinessId()).stream()
                .filter(r -> r[0].equals(id)).findFirst().orElse(null);
        return toResponse(w, totals);
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        CurrentUser me = CurrentUser.get();
        Warehouse w = new Warehouse();
        w.setBusinessId(me.businessId());
        apply(w, request);
        if (warehouses.findFirstByBusinessIdAndDefaultWarehouseTrue(me.businessId()).isEmpty()) {
            w.setDefaultWarehouse(true);
        }
        warehouses.save(w);
        audit.record(me.businessId(), me.membershipId(), "WAREHOUSE_CREATED", "Warehouse", w.getId(),
                Map.of("name", w.getName(), "code", w.getCode()));
        return get(w.getId());
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest request) {
        CurrentUser me = CurrentUser.get();
        Warehouse w = find(id);
        apply(w, request);
        audit.record(me.businessId(), me.membershipId(), "WAREHOUSE_UPDATED", "Warehouse", id, Map.of("name", w.getName()));
        return get(id);
    }

    @Transactional
    public WarehouseResponse setArchived(Long id, boolean archived) {
        CurrentUser me = CurrentUser.get();
        Warehouse w = find(id);
        if (archived) {
            if (w.isDefaultWarehouse()) throw new ConflictException("Choose another default warehouse before archiving this one");
            if (balances.warehouseHasStock(id)) throw new ConflictException("Move or reduce all stock in this warehouse before archiving it");
        }
        w.setArchived(archived);
        audit.record(me.businessId(), me.membershipId(), archived ? "WAREHOUSE_ARCHIVED" : "WAREHOUSE_RESTORED",
                "Warehouse", id, Map.of("name", w.getName()));
        return get(id);
    }

    @Transactional
    public WarehouseResponse makeDefault(Long id) {
        CurrentUser me = CurrentUser.get();
        Warehouse w = find(id);
        if (w.isArchived()) throw new ConflictException("An archived warehouse cannot be the default");
        warehouses.findByBusinessIdOrderByNameAsc(me.businessId()).forEach(x -> x.setDefaultWarehouse(x.getId().equals(id)));
        audit.record(me.businessId(), me.membershipId(), "WAREHOUSE_DEFAULT_CHANGED", "Warehouse", id, Map.of("name", w.getName()));
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> locations(Long warehouseId) {
        find(warehouseId);
        return locations.findByWarehouseIdOrderByCodeAsc(warehouseId).stream().map(WarehouseService::toResponse).toList();
    }

    @Transactional
    public LocationResponse createLocation(Long warehouseId, LocationRequest request) {
        CurrentUser me = CurrentUser.get();
        StorageLocation l = new StorageLocation();
        l.setBusinessId(me.businessId());
        l.setWarehouse(find(warehouseId));
        l.setCode(request.code().trim());
        l.setDescription(AuthService.blankToNull(request.description()));
        locations.save(l);
        audit.record(me.businessId(), me.membershipId(), "LOCATION_CREATED", "StorageLocation", l.getId(),
                Map.of("code", l.getCode(), "warehouseId", warehouseId));
        return toResponse(l);
    }

    @Transactional
    public LocationResponse updateLocation(Long locationId, LocationRequest request) {
        CurrentUser me = CurrentUser.get();
        StorageLocation l = location(locationId);
        l.setCode(request.code().trim());
        l.setDescription(AuthService.blankToNull(request.description()));
        audit.record(me.businessId(), me.membershipId(), "LOCATION_UPDATED", "StorageLocation", locationId,
                Map.of("code", l.getCode()));
        return toResponse(l);
    }

    @Transactional
    public LocationResponse setLocationArchived(Long locationId, boolean archived) {
        CurrentUser me = CurrentUser.get();
        StorageLocation l = location(locationId);
        l.setArchived(archived);
        audit.record(me.businessId(), me.membershipId(), archived ? "LOCATION_ARCHIVED" : "LOCATION_RESTORED",
                "StorageLocation", locationId, null);
        return toResponse(l);
    }

    public Warehouse find(Long id) {
        return warehouses.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Warehouse", id));
    }

    private StorageLocation location(Long id) {
        return locations.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Storage location", id));
    }

    private static void apply(Warehouse w, WarehouseRequest r) {
        w.setName(r.name().trim());
        w.setCode(r.code().trim().toUpperCase());
        w.setAddress(AuthService.blankToNull(r.address()));
        w.setBranch(r.branch());
    }

    private static WarehouseResponse toResponse(Warehouse w, Object[] totals) {
        long products = totals != null ? (Long) totals[1] : 0L;
        BigDecimal qty = totals != null ? (BigDecimal) totals[2] : BigDecimal.ZERO;
        return new WarehouseResponse(w.getId(), w.getName(), w.getCode(), w.getAddress(), w.isBranch(),
                w.isDefaultWarehouse(), w.isArchived(), products, qty);
    }

    private static LocationResponse toResponse(StorageLocation l) {
        return new LocationResponse(l.getId(), l.getWarehouse().getId(), l.getCode(), l.getDescription(), l.isArchived());
    }
}
