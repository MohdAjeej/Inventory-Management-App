package com.b2binventory.security;

import com.b2binventory.exception.ForbiddenException;

/** Service-level permission checks for decisions that depend on the request content. */
public final class Permissions {

    private Permissions() {
    }

    /** Purchase costs, margins and profit are shown only to members who manage products, purchases or analytics. */
    public static boolean canSeeCost() {
        CurrentUser me = CurrentUser.get();
        return me.has(Permission.ANALYTICS_VIEW) || me.has(Permission.PURCHASE_MANAGE) || me.has(Permission.PRODUCT_EDIT);
    }

    public static void requireAny(Permission... permissions) {
        CurrentUser me = CurrentUser.get();
        for (Permission p : permissions) if (me.has(p)) return;
        throw new ForbiddenException("You do not have permission: " + permissions[0].getLabel());
    }

    public static void require(Permission permission) {
        if (!CurrentUser.get().has(permission)) {
            throw new ForbiddenException("You do not have permission: " + permission.getLabel());
        }
    }
}
