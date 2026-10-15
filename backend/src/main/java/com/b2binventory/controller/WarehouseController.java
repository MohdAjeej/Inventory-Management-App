package com.b2binventory.controller;

import com.b2binventory.dto.StockDtos.*;
import com.b2binventory.service.WarehouseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Warehouses")
public class WarehouseController {

    private final WarehouseService warehouses;

    public WarehouseController(WarehouseService warehouses) {
        this.warehouses = warehouses;
    }

    @GetMapping("/warehouses")
    public List<WarehouseResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return warehouses.list(includeArchived);
    }

    @GetMapping("/warehouses/{id}")
    public WarehouseResponse get(@PathVariable Long id) {
        return warehouses.get(id);
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE')")
    public WarehouseResponse create(@Valid @RequestBody WarehouseRequest request) {
        return warehouses.create(request);
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE')")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return warehouses.update(id, request);
    }

    @PostMapping("/warehouses/{id}/archive")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE')")
    public WarehouseResponse archive(@PathVariable Long id) {
        return warehouses.setArchived(id, true);
    }

    @PostMapping("/warehouses/{id}/restore")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE')")
    public WarehouseResponse restore(@PathVariable Long id) {
        return warehouses.setArchived(id, false);
    }

    @PostMapping("/warehouses/{id}/default")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE')")
    public WarehouseResponse makeDefault(@PathVariable Long id) {
        return warehouses.makeDefault(id);
    }

    @GetMapping("/warehouses/{id}/locations")
    public List<LocationResponse> locations(@PathVariable Long id) {
        return warehouses.locations(id);
    }

    @PostMapping("/warehouses/{id}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE')")
    public LocationResponse createLocation(@PathVariable Long id, @Valid @RequestBody LocationRequest request) {
        return warehouses.createLocation(id, request);
    }

    @PutMapping("/locations/{locationId}")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE')")
    public LocationResponse updateLocation(@PathVariable Long locationId, @Valid @RequestBody LocationRequest request) {
        return warehouses.updateLocation(locationId, request);
    }

    @PostMapping("/locations/{locationId}/archive")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE')")
    public LocationResponse archiveLocation(@PathVariable Long locationId) {
        return warehouses.setLocationArchived(locationId, true);
    }

    @PostMapping("/locations/{locationId}/restore")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE')")
    public LocationResponse restoreLocation(@PathVariable Long locationId) {
        return warehouses.setLocationArchived(locationId, false);
    }
}
