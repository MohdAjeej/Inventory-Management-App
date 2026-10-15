package com.b2binventory.controller;

import com.b2binventory.dto.PartyDtos.PartyRequest;
import com.b2binventory.dto.PartyDtos.PartyResponse;
import com.b2binventory.service.PartyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Permissions are checked in PartyService: customers need sales permissions, suppliers need purchase permissions. */
@RestController
@RequestMapping("/api")
@Tag(name = "Customers & suppliers")
public class PartyController {

    private final PartyService parties;

    public PartyController(PartyService parties) {
        this.parties = parties;
    }

    @GetMapping("/customers")
    public List<PartyResponse> customers(@RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "false") boolean includeArchived) {
        return parties.customers(q, includeArchived);
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public PartyResponse createCustomer(@Valid @RequestBody PartyRequest request) {
        return parties.createCustomer(request);
    }

    @PutMapping("/customers/{id}")
    public PartyResponse updateCustomer(@PathVariable Long id, @Valid @RequestBody PartyRequest request) {
        return parties.updateCustomer(id, request);
    }

    @PostMapping("/customers/{id}/archive")
    public PartyResponse archiveCustomer(@PathVariable Long id) {
        return parties.setCustomerArchived(id, true);
    }

    @PostMapping("/customers/{id}/restore")
    public PartyResponse restoreCustomer(@PathVariable Long id) {
        return parties.setCustomerArchived(id, false);
    }

    @GetMapping("/suppliers")
    public List<PartyResponse> suppliers(@RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "false") boolean includeArchived) {
        return parties.suppliers(q, includeArchived);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public PartyResponse createSupplier(@Valid @RequestBody PartyRequest request) {
        return parties.createSupplier(request);
    }

    @PutMapping("/suppliers/{id}")
    public PartyResponse updateSupplier(@PathVariable Long id, @Valid @RequestBody PartyRequest request) {
        return parties.updateSupplier(id, request);
    }

    @PostMapping("/suppliers/{id}/archive")
    public PartyResponse archiveSupplier(@PathVariable Long id) {
        return parties.setSupplierArchived(id, true);
    }

    @PostMapping("/suppliers/{id}/restore")
    public PartyResponse restoreSupplier(@PathVariable Long id) {
        return parties.setSupplierArchived(id, false);
    }
}
