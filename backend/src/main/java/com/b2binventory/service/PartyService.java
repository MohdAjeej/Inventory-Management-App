package com.b2binventory.service;

import com.b2binventory.dto.PartyDtos.PartyRequest;
import com.b2binventory.dto.PartyDtos.PartyResponse;
import com.b2binventory.entity.Customer;
import com.b2binventory.entity.Supplier;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.CustomerRepository;
import com.b2binventory.repository.SupplierRepository;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.security.Permission;
import com.b2binventory.security.Permissions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.b2binventory.service.AuthService.blankToNull;

/** Customers (used on sales) and suppliers (used on purchases and products). Always scoped to the caller's business. */
@Service
public class PartyService {

    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final AuditService audit;

    public PartyService(CustomerRepository customers, SupplierRepository suppliers, AuditService audit) {
        this.customers = customers;
        this.suppliers = suppliers;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ customers

    @Transactional(readOnly = true)
    public List<PartyResponse> customers(String q, boolean includeArchived) {
        Permissions.requireAny(Permission.SALE_RECORD, Permission.SALE_CANCEL, Permission.ANALYTICS_VIEW);
        return customers.search(CurrentUser.get().businessId(), like(q), includeArchived).stream()
                .map(PartyService::toResponse).toList();
    }

    @Transactional
    public PartyResponse createCustomer(PartyRequest r) {
        Permissions.require(Permission.SALE_RECORD);
        Customer c = new Customer();
        c.setBusinessId(CurrentUser.get().businessId());
        apply(c, r);
        customers.save(c);
        record("CUSTOMER_CREATED", "Customer", c.getId(), c.getName());
        return toResponse(c);
    }

    @Transactional
    public PartyResponse updateCustomer(Long id, PartyRequest r) {
        Permissions.require(Permission.SALE_RECORD);
        Customer c = customer(id);
        apply(c, r);
        return toResponse(c);
    }

    @Transactional
    public PartyResponse setCustomerArchived(Long id, boolean archived) {
        Permissions.require(Permission.SALE_RECORD);
        Customer c = customer(id);
        c.setArchived(archived);
        return toResponse(c);
    }

    /** Used by sales: creates a customer from the name typed on the sale. */
    Customer quickCustomer(String name, String phone) {
        Customer c = new Customer();
        c.setBusinessId(CurrentUser.get().businessId());
        c.setName(name.trim());
        c.setPhone(blankToNull(phone));
        return customers.save(c);
    }

    public Customer customer(Long id) {
        return customers.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Customer", id));
    }

    // ------------------------------------------------------------------ suppliers

    @Transactional(readOnly = true)
    public List<PartyResponse> suppliers(String q, boolean includeArchived) {
        Permissions.requireAny(Permission.PURCHASE_MANAGE, Permission.PRODUCT_EDIT, Permission.PRODUCT_CREATE);
        return suppliers.search(CurrentUser.get().businessId(), like(q), includeArchived).stream()
                .map(PartyService::toResponse).toList();
    }

    @Transactional
    public PartyResponse createSupplier(PartyRequest r) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        Supplier s = new Supplier();
        s.setBusinessId(CurrentUser.get().businessId());
        apply(s, r);
        suppliers.save(s);
        record("SUPPLIER_CREATED", "Supplier", s.getId(), s.getName());
        return toResponse(s);
    }

    @Transactional
    public PartyResponse updateSupplier(Long id, PartyRequest r) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        Supplier s = supplier(id);
        apply(s, r);
        return toResponse(s);
    }

    @Transactional
    public PartyResponse setSupplierArchived(Long id, boolean archived) {
        Permissions.require(Permission.PURCHASE_MANAGE);
        Supplier s = supplier(id);
        s.setArchived(archived);
        return toResponse(s);
    }

    Supplier quickSupplier(String name) {
        Supplier s = new Supplier();
        s.setBusinessId(CurrentUser.get().businessId());
        s.setName(name.trim());
        return suppliers.save(s);
    }

    public Supplier supplier(Long id) {
        return suppliers.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Supplier", id));
    }

    // ------------------------------------------------------------------ helpers

    private void record(String action, String type, Long id, String name) {
        CurrentUser me = CurrentUser.get();
        audit.record(me.businessId(), me.membershipId(), action, type, id, Map.of("name", name));
    }

    private static String like(String q) {
        return q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static void apply(Customer c, PartyRequest r) {
        c.setName(r.name().trim());
        c.setPhone(blankToNull(r.phone()));
        c.setEmail(blankToNull(r.email()));
        c.setAddress(blankToNull(r.address()));
        c.setNotes(blankToNull(r.notes()));
    }

    private static void apply(Supplier s, PartyRequest r) {
        s.setName(r.name().trim());
        s.setPhone(blankToNull(r.phone()));
        s.setEmail(blankToNull(r.email()));
        s.setAddress(blankToNull(r.address()));
        s.setTaxId(blankToNull(r.taxId()));
        s.setNotes(blankToNull(r.notes()));
    }

    static PartyResponse toResponse(Customer c) {
        return new PartyResponse(c.getId(), c.getName(), c.getPhone(), c.getEmail(), c.getAddress(), null, c.getNotes(),
                c.isArchived(), c.getCreatedAt());
    }

    static PartyResponse toResponse(Supplier s) {
        return new PartyResponse(s.getId(), s.getName(), s.getPhone(), s.getEmail(), s.getAddress(), s.getTaxId(),
                s.getNotes(), s.isArchived(), s.getCreatedAt());
    }
}
