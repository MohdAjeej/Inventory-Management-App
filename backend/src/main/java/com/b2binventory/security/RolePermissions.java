package com.b2binventory.security;

import com.b2binventory.entity.Role;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.b2binventory.security.Permission.*;

/** Default permissions for each role; per-membership overrides are applied on top. */
public final class RolePermissions {

    private static final Map<Role, Set<Permission>> DEFAULTS = new EnumMap<>(Role.class);

    static {
        DEFAULTS.put(Role.ADMIN, EnumSet.allOf(Permission.class));
        DEFAULTS.put(Role.INVENTORY_MANAGER, EnumSet.of(
                PRODUCT_VIEW, PRODUCT_CREATE, PRODUCT_EDIT, PRODUCT_ARCHIVE,
                STOCK_ADD, STOCK_REDUCE, STOCK_ADJUST, STOCK_TRANSFER, STOCK_COUNT,
                REPORT_VIEW, REPORT_EXPORT, CATEGORY_MANAGE, WAREHOUSE_MANAGE,
                SALE_RECORD, SALE_CANCEL, ANALYTICS_VIEW, PURCHASE_MANAGE));
        DEFAULTS.put(Role.STAFF, EnumSet.of(
                PRODUCT_VIEW, STOCK_ADD, STOCK_REDUCE, STOCK_COUNT, SALE_RECORD));
    }

    private RolePermissions() {
    }

    public static Set<Permission> defaultsFor(Role role) {
        return Collections.unmodifiableSet(DEFAULTS.get(role));
    }

    public static Set<Permission> effective(Role role, Map<Permission, Boolean> overrides) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        result.addAll(DEFAULTS.get(role));
        if (overrides != null) {
            overrides.forEach((permission, granted) -> {
                if (Boolean.TRUE.equals(granted)) result.add(permission);
                else result.remove(permission);
            });
        }
        // Admins can never lock themselves out of employee administration.
        if (role == Role.ADMIN) result.add(EMPLOYEE_MANAGE);
        return result;
    }
}
