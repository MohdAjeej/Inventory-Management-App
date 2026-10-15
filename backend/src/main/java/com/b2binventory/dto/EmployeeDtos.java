package com.b2binventory.dto;

import com.b2binventory.entity.InvitationStatus;
import com.b2binventory.entity.MembershipStatus;
import com.b2binventory.entity.Role;
import com.b2binventory.security.Permission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    public record EmployeeResponse(
            Long membershipId, Long userId, String fullName, String email, String phone, Role role,
            MembershipStatus status, Set<Permission> permissions, Map<Permission, Boolean> overrides, Instant joinedAt) {
    }

    public record ChangeRoleRequest(@NotNull(message = "Role is required") Role role) {
    }

    /** Full replacement of the per-employee overrides; an empty map restores the role defaults. */
    public record PermissionOverridesRequest(@NotNull Map<Permission, Boolean> overrides) {
    }

    public record InviteRequest(
            @NotBlank(message = "Email is required") @Email(message = "Email is invalid") String email,
            @NotNull(message = "Role is required") Role role) {
    }

    public record InvitationResponse(
            Long id, String email, Role role, String code, InvitationStatus status,
            String invitedBy, Instant expiresAt, Instant createdAt) {
    }

    public record PermissionInfo(Permission permission, String label) {
    }

    public record PermissionCatalog(List<PermissionInfo> permissions, Map<Role, Set<Permission>> roleDefaults) {
    }
}
