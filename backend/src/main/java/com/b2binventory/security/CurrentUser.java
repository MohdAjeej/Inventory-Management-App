package com.b2binventory.security;

import com.b2binventory.entity.Role;
import com.b2binventory.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/**
 * The authenticated principal. The business is always taken from here (derived from the
 * server-side membership), never from request parameters.
 */
public record CurrentUser(
        Long userId,
        Long membershipId,
        Long businessId,
        String email,
        String fullName,
        Role role,
        Set<Permission> permissions
) {
    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        throw new UnauthorizedException("Not authenticated");
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
