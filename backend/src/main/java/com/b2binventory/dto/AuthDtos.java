package com.b2binventory.dto;

import com.b2binventory.entity.Role;
import com.b2binventory.security.Permission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Business name is required") @Size(max = 200) String businessName,
            @Size(max = 120) String businessType,
            @Email(message = "Business email is invalid") @Size(max = 200) String businessEmail,
            @Size(max = 50) String businessPhone,
            @Size(max = 500) String address,
            @Size(max = 120) String city,
            @Size(max = 120) String state,
            @Size(max = 120) String country,
            @NotBlank(message = "Your name is required") @Size(max = 200) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Email is invalid") @Size(max = 200) String email,
            @Size(max = 50) String phone,
            @NotBlank(message = "Password is required") @Size(min = 8, max = 100, message = "Password must be at least 8 characters") String password,
            /* IANA timezone of the registering device; defaults to UTC */
            @Size(max = 64) String timezone
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password,
            Long businessId
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record SwitchBusinessRequest(@NotNull Long businessId) {
    }

    public record AcceptInvitationRequest(
            @NotBlank(message = "Invitation code is required") String code,
            @Size(max = 200) String fullName,
            @Size(max = 50) String phone,
            @NotBlank(message = "Password is required") @Size(min = 8, max = 100, message = "Password must be at least 8 characters") String password
    ) {
    }

    public record InvitationPreview(String businessName, String email, Role role, boolean existingUser, Instant expiresAt) {
    }

    public record MembershipSummary(Long membershipId, Long businessId, String businessName, Role role,
                                    String businessType) {
    }

    public record MeResponse(
            Long userId,
            String fullName,
            String email,
            String phone,
            Long membershipId,
            Long businessId,
            String businessName,
            Role role,
            Set<Permission> permissions,
            List<MembershipSummary> memberships
    ) {
    }

    public record AuthResponse(String accessToken, String refreshToken, long expiresIn, MeResponse user) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 200) String fullName,
            @Size(max = 50) String phone
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100, message = "Password must be at least 8 characters") String newPassword
    ) {
    }
}
